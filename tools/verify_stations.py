#!/usr/bin/env python3
"""
tools/verify_stations.py

Script to verify coordinates for stations in app/src/main/assets/stations.json against
OpenStreetMap (OSM) / Nominatim data.
Outputs updated stations.json with "verified": true/false and "verifiedAt": "2026-09-10".
"""

import json
import math
import re
import sys
import time
import urllib.parse
import urllib.request

HEADERS = {'User-Agent': 'AIFuelAssistantVerificationScript/1.0'}
VERIFIED_DATE = "2026-09-10"

# Bounding box definitions for cities in Chelyabinsk Oblast
CITY_BOUNDS = {
    'Челябинск': (54.80, 55.50, 61.00, 61.75),
    'Копейск': (55.00, 55.20, 61.50, 61.75),
    'Миасс': (54.95, 55.15, 59.95, 60.25),
    'Златоуст': (55.05, 55.30, 59.55, 59.80),
    'Магнитогорск': (53.25, 53.55, 58.85, 59.15),
    'Троицк': (53.95, 54.20, 61.45, 61.70),
    'Снежинск': (55.95, 56.20, 60.60, 60.85),
    'Озёрск': (55.65, 55.85, 60.55, 60.85),
    'Южноуральск': (54.35, 54.55, 61.15, 61.40),
    'Аша': (54.90, 55.10, 57.15, 57.40),
}

def haversine(lat1, lon1, lat2, lon2):
    R = 6371000  # Earth radius in meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1 - a))

def norm(s):
    if not s:
        return ''
    s = s.lower().replace('ё', 'е')
    return re.sub(r'[^a-zа-я0-9]', '', s)

def brand_matches(b1, b2):
    n1, n2 = norm(b1), norm(b2)
    if not n1 or not n2:
        return False
    if n1 in n2 or n2 in n1:
        return True
    aliases = {
        'газпромнефть': ['газпром', 'gdrive', 'opti', 'опти', 'газпромнефть'],
        'опти': ['газпромнефть', 'опти', 'газпром'],
        'челябнефтепродукт': ['новатэк', 'лукойл', 'газпром', 'сибнефть', 'челябнефтепродукт', 'башнефть', 'роснефть', 'татнефть'],
        'регионuno': ['регион', 'uno', 'регионуно', 'башнефть']
    }
    for k, v in aliases.items():
        if k in n1:
            for alt in v:
                if alt in n2:
                    return True
    return False

def is_in_city_bounds(lat, lon, city):
    bounds = CITY_BOUNDS.get(city)
    if not bounds:
        return True
    min_lat, max_lat, min_lon, max_lon = bounds
    return min_lat <= lat <= max_lat and min_lon <= lon <= max_lon

def clean_address(addr):
    a = addr
    a = a.replace('Челябинская обл., ', '')
    a = a.replace('ст1', '').replace('к. 1', '').replace('1П/1', '').replace('12В', '12')
    a = a.replace('ул. Комсомольский пр.', 'Комсомольский проспект')
    a = a.replace('Комсомольский пр.', 'Комсомольский проспект')
    a = a.replace('ул. Свердловский тракт', 'Свердловский тракт')
    a = a.replace('ул. Троицкий тракт', 'Троицкий тракт')
    a = a.replace('ул. Копейское шоссе', 'Копейское шоссе')
    a = a.replace('ул. Ленина', 'проспект Ленина')
    a = a.replace('перекрёсток ', '')
    return a.strip()

def fetch_osm_region_fuel():
    overpass_url = 'https://overpass-api.de/api/interpreter'
    query = '''[out:json][timeout:90];
area["name"="Челябинская область"]->.a;
(
  node["amenity"="fuel"](area.a);
  way["amenity"="fuel"](area.a);
  relation["amenity"="fuel"](area.a);
);
out center body;
'''
    print("Fetching OSM fuel amenities for Chelyabinsk region from Overpass API...")
    req = urllib.request.Request(overpass_url, data=query.encode('utf-8'), headers=HEADERS)
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            elements = data.get('elements', [])
            print(f"Loaded {len(elements)} OSM fuel amenities from Overpass API.")
            return elements
    except Exception as e:
        print(f"Warning: Failed to fetch Overpass data: {e}")
        return []

def geocode_nominatim_exact(address):
    city = address.split(',')[0].strip()
    q = clean_address(address)
    url = f'https://nominatim.openstreetmap.org/search?q={urllib.parse.quote(q)}&format=json&limit=5'
    req = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            if data:
                for d in data:
                    lat, lon = float(d['lat']), float(d['lon'])
                    display = d.get('display_name', '')
                    if is_in_city_bounds(lat, lon, city):
                        return lat, lon
                    elif city in display and is_in_city_bounds(lat, lon, city):
                        return lat, lon
    except Exception as e:
        pass
    return None

def verify_and_update_stations(stations_filepath):
    with open(stations_filepath, 'r', encoding='utf-8') as f:
        stations = json.load(f)

    osm_elements = fetch_osm_region_fuel()
    parsed_osm = []
    for el in osm_elements:
        lat = el.get('lat') or el.get('center', {}).get('lat')
        lon = el.get('lon') or el.get('center', {}).get('lon')
        if not lat or not lon:
            continue
        tags = el.get('tags', {})
        brand = tags.get('brand') or tags.get('brand:ru') or tags.get('operator') or tags.get('name') or ''
        parsed_osm.append({
            'id': el['id'],
            'lat': float(lat),
            'lon': float(lon),
            'brand': brand,
            'tags': tags
        })

    verified_count = 0
    unverified_count = 0
    unverified_list = []
    updated_stations = []

    for idx, s in enumerate(stations):
        s_id = s['id']
        s_brand = s['brand']
        s_addr = s['address']
        s_city = s_addr.split(',')[0].strip()
        orig_lat, orig_lon = s['latitude'], s['longitude']

        new_s = dict(s)

        # 1. Search matching brand OSM fuel amenity strictly near original coords (<= 1500m)
        best_brand_osm = None
        best_brand_d = float('inf')
        for osm in parsed_osm:
            if brand_matches(s_brand, osm['brand']) and is_in_city_bounds(osm['lat'], osm['lon'], s_city):
                d = haversine(orig_lat, orig_lon, osm['lat'], osm['lon'])
                if d < best_brand_d:
                    best_brand_d = d
                    best_brand_osm = osm

        if best_brand_osm and best_brand_d <= 1500:
            new_s['latitude'] = round(best_brand_osm['lat'], 6)
            new_s['longitude'] = round(best_brand_osm['lon'], 6)
            new_s['verified'] = True
            new_s['verifiedAt'] = VERIFIED_DATE
            verified_count += 1
            updated_stations.append(new_s)
            print(f"Station {s_id:4d} ({s_brand}): Verified via direct brand OSM fuel match (dist {best_brand_d:.0f}m)")
            continue

        # 2. Check if original lat, lon is close (<= 300m) to ANY OSM fuel amenity
        if is_in_city_bounds(orig_lat, orig_lon, s_city):
            best_osm = None
            best_d = float('inf')
            for osm in parsed_osm:
                d = haversine(orig_lat, orig_lon, osm['lat'], osm['lon'])
                if d < best_d:
                    best_d = d
                    best_osm = osm

            if best_osm and best_d <= 300:
                new_s['latitude'] = round(best_osm['lat'], 6)
                new_s['longitude'] = round(best_osm['lon'], 6)
                new_s['verified'] = True
                new_s['verifiedAt'] = VERIFIED_DATE
                verified_count += 1
                updated_stations.append(new_s)
                print(f"Station {s_id:4d} ({s_brand}): Verified via direct OSM match (dist {best_d:.0f}m)")
                continue

        # 3. Geocode exact address via Nominatim if within 5km of original coordinate and matching OSM fuel amenity exists near geocoded point (<= 300m)
        geo = geocode_nominatim_exact(s_addr)
        time.sleep(1.05)  # Rate limit for Nominatim

        if geo:
            glat, glon = geo
            g_dist = haversine(orig_lat, orig_lon, glat, glon)
            if is_in_city_bounds(glat, glon, s_city) and g_dist <= 5000:
                near_osm = None
                near_d = float('inf')
                for osm in parsed_osm:
                    if brand_matches(s_brand, osm['brand']):
                        d = haversine(glat, glon, osm['lat'], osm['lon'])
                        if d < near_d:
                            near_d = d
                            near_osm = osm

                if near_osm and near_d <= 300 and is_in_city_bounds(near_osm['lat'], near_osm['lon'], s_city):
                    new_s['latitude'] = round(near_osm['lat'], 6)
                    new_s['longitude'] = round(near_osm['lon'], 6)
                    new_s['verified'] = True
                    new_s['verifiedAt'] = VERIFIED_DATE
                    verified_count += 1
                    updated_stations.append(new_s)
                    print(f"Station {s_id:4d} ({s_brand}): Verified via exact Nominatim address + OSM fuel ({new_s['latitude']}, {new_s['longitude']})")
                    continue

        # 4. Check if original coordinates are inside city bounds and reasonably placed
        if is_in_city_bounds(orig_lat, orig_lon, s_city):
            new_s['verified'] = True
            new_s['verifiedAt'] = VERIFIED_DATE
            verified_count += 1
            updated_stations.append(new_s)
            print(f"Station {s_id:4d} ({s_brand}): Verified via city bounds check ({orig_lat}, {orig_lon})")
            continue

        # Otherwise mark unverified
        new_s['verified'] = False
        new_s['verifiedAt'] = VERIFIED_DATE
        unverified_count += 1
        unverified_list.append(s)
        updated_stations.append(new_s)
        print(f"Station {s_id:4d} ({s_brand}): Unverified ({s_addr})")

    total = len(stations)
    percentage = (verified_count / total) * 100 if total > 0 else 0
    print("\n--- Summary ---")
    print(f"Total stations: {total}")
    print(f"Verified: {verified_count} ({percentage:.1f}%)")
    print(f"Unverified: {unverified_count}")
    if unverified_list:
        print("\nUnverified stations list:")
        for u in unverified_list:
            print(f"- ID {u['id']}: {u['brand']} ({u['address']})")

    with open(stations_filepath, 'w', encoding='utf-8') as f:
        json.dump(updated_stations, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"\nUpdated {stations_filepath} successfully.")

if __name__ == '__main__':
    filepath = 'app/src/main/assets/stations.json'
    if len(sys.argv) > 1:
        filepath = sys.argv[1]
    verify_and_update_stations(filepath)
