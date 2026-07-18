package com.navrot.aifuelassistant.data

object VehicleCatalog {

    val brands = listOf(
        "Acura", "Audi", "BMW", "BYD", "Changan", "Chery", "Chevrolet",
        "Citroën", "Datsun", "Ford", "Geely", "Haval", "Honda", "Hyundai",
        "Infiniti", "Jaguar", "Kia", "Lada (ВАЗ)", "Land Rover", "Lexus",
        "Mazda", "Mercedes-Benz", "Mitsubishi", "Nissan", "Opel", "Peugeot",
        "Porsche", "Renault", "Skoda", "Subaru", "Suzuki", "Tesla", "Toyota",
        "Volkswagen", "Volvo", "ГАЗ", "УАЗ"
    ).sorted()

    fun getModels(brand: String): List<String> {
        return when (brand) {
            "Acura" -> listOf("TLX", "RLX", "MDX", "RDX", "ILX", "TSX", "ZDX", "NSX", "Integra", "Legend", "RSX")
            "Audi" -> listOf("A1", "A3", "A4", "A5", "A6", "A7", "A8", "Q3", "Q5", "Q7", "Q8", "TT", "e-tron", "e-tron GT", "RS3", "RS4", "RS6", "RS Q8")
            "BMW" -> listOf("1 Series", "2 Series", "3 Series", "4 Series", "5 Series", "6 Series", "7 Series", "8 Series", "X1", "X2", "X3", "X4", "X5", "X6", "X7", "Z4", "M3", "M5", "i4", "iX", "iX3")
            "BYD" -> listOf("Song", "Han", "Tang", "Seal", "Dolphin", "Atto 3", "Qin", "Yuan", "Seagull", "Sealion")
            "Changan" -> listOf("CS35", "CS55", "CS75", "CS95", "UNI-K", "UNI-T", "Alsvin", "Eado", "Hunter")
            "Chery" -> listOf("Tiggo 2", "Tiggo 3", "Tiggo 4", "Tiggo 7", "Tiggo 8", "Tiggo 9", "Arrizo", "Omoda 5", "QQ")
            "Chevrolet" -> listOf("Aveo", "Bolt", "Camaro", "Captiva", "Colorado", "Cruze", "Epica", "Equinox", "Lacetti", "Niva", "Orlando", "Silverado", "Spark", "Suburban", "Tahoe", "Trailblazer")
            "Citroën" -> listOf("Ami", "Berlingo", "C3", "C4", "C5", "C-Elysée", "Jumper", "Jumpy", "Spacetourer")
            "Datsun" -> listOf("On-Do", "Mi-Do", "redi-Go", "Go", "Cross")
            "Ford" -> listOf("Bronco", "Custom", "EcoSport", "Explorer", "F-150", "Focus", "Galaxy", "Kuga", "Maverick", "Mondeo", "Mustang", "Puma", "Ranger", "S-Max", "Transit")
            "Geely" -> listOf("Atlas", "Azkarra", "Binyue", "Boyue", "Coolray", "Emgrand", "Geometry A", "Geometry C", "Monjaro", "Okavango", "Preface", "Tugella")
            "Haval" -> listOf("Big Dog", "Cool Dog", "Dargo", "Dragon", "F5", "F7", "F7x", "H2", "H5", "H6", "H6S", "H8", "H9", "Jolion", "M6", "Raptor")
            "Honda" -> listOf("Accord", "Civic", "CR-V", "CR-Z", "e", "HR-V", "Insight", "Jazz", "Legend", "Passport", "Pilot", "Ridgeline", "Stream")
            "Hyundai" -> listOf("Accent", "Creta", "Elantra", "i30", "i40", "Kona", "Nexo", "Palisade", "Santa Fe", "Solaris", "Sonata", "Staria", "Tucson", "Venue")
            "Infiniti" -> listOf("EX35", "FX35", "FX50", "G35", "G37", "M35", "M45", "Q30", "Q50", "Q70", "QX30", "QX50", "QX60", "QX80")
            "Jaguar" -> listOf("E-Pace", "F-Pace", "F-Type", "F-Type R", "F-Type SVR", "I-Pace", "S-Type", "XE", "XF", "XJ", "XK", "X-Type")
            "Kia" -> listOf("Carnival", "Cerato", "Ceed", "K5", "Mohave", "Optima", "Picanto", "Rio", "Seltos", "Sorento", "Soul", "Sportage", "Stinger")
            "Lada (ВАЗ)" -> listOf("4x4", "Granta", "Granta Cross", "Kalina", "Largus", "Niva", "Niva Travel", "Priora", "Samara", "Vesta", "Vesta Cross", "XRAY")
            "Land Rover" -> listOf("Defender", "Discovery", "Discovery Sport", "Freelander", "Range Rover", "Range Rover Evoque", "Range Rover Sport", "Range Rover Velar")
            "Lexus" -> listOf("ES", "GS", "GX", "IS", "LC", "LM", "LS", "LX", "NX", "RC", "RZ", "RX", "TX", "UX")
            "Mazda" -> listOf("2", "3", "6", "BT-50", "CX-3", "CX-30", "CX-5", "CX-50", "CX-60", "CX-90", "CX-9", "MX-5")
            "Mercedes-Benz" -> listOf("A-Class", "C-Class", "CLA", "CLS", "E-Class", "EQA", "EQC", "EQE", "EQS", "GLA", "GLB", "GLC", "GLE", "GLS", "GT", "S-Class", "SL", "Sprinter", "Vito")
            "Mitsubishi" -> listOf("ASX", "Colt", "Eclipse Cross", "Galant", "Grandis", "L200", "Lancer", "Mirage", "Montero", "Outlander", "Pajero", "Pajero Sport")
            "Nissan" -> listOf("Almera", "GT-R", "Juke", "Leaf", "Maxima", "Murano", "Navara", "Note", "Pathfinder", "Patrol", "Qashqai", "Sentra", "Teana", "Tiida", "X-Trail", "Z")
            "Opel" -> listOf("Adam", "Antara", "Astra", "Combo", "Corsa", "Crossland", "Grandland", "Insignia", "Karl", "Meriva", "Mokka", "Movano", "Vectra", "Vivaro", "Zafira")
            "Peugeot" -> listOf("2008", "208", "3008", "308", "408", "5008", "508", "Boxer", "Expert", "Landtrek", "Partner", "Rifter", "Traveller")
            "Porsche" -> listOf("911", "718 Boxster", "718 Cayman", "Carrera", "Cayenne", "Cayenne Coupe", "GT3", "GT3 RS", "Macan", "Macan EV", "Panamera", "Taycan", "Turbo S")
            "Renault" -> listOf("Arkana", "Clio", "Duster", "Espace", "Fluence", "Kangoo", "Kaptur", "Koleos", "Latitude", "Logan", "Master", "Megane", "Sandero", "Scenic", "Symbol", "Talisman", "Trafic")
            "Skoda" -> listOf("Citigo", "Enyaq", "Fabia", "Kamiq", "Karoq", "Kodiaq", "Kushaq", "Octavia", "Rapid", "Roomster", "Scala", "Slavia", "Superb", "Yeti")
            "Subaru" -> listOf("Ascent", "Baja", "BRZ", "Forester", "Impreza", "Justy", "Legacy", "Levorg", "Outback", "Tribeca", "WRX", "WRX STI", "XV")
            "Suzuki" -> listOf("Baleno", "Celerio", "Ertiga", "Grand Vitara", "Ignis", "Jimny", "Kizashi", "Liana", "S-Cross", "Splash", "Swift", "SX4", "Vitara")
            "Tesla" -> listOf("Cybertruck", "Model 3", "Model S", "Model X", "Model Y", "Roadster", "Semi")
            "Toyota" -> listOf("Auris", "Avensis", "C-HR", "Camry", "Corolla", "Fortuner", "Highlander", "Hilux", "Land Cruiser", "Prius", "RAV4", "Verso", "Yaris")
            "Volkswagen" -> listOf("Amarok", "Arteon", "Caravelle", "Golf", "Jetta", "Multivan", "Passat", "Polo", "T-Roc", "Taos", "Teramont", "Tiguan", "Touareg", "Transporter")
            "Volvo" -> listOf("C30", "C40", "C70", "EX30", "EX90", "S60", "S80", "S90", "V40", "V60", "V90", "XC40", "XC60", "XC70", "XC90")
            "ГАЗ" -> listOf("ГАЗ-66", "ГАЗ-3307", "ГАЗ-3309", "ГАЗель NN", "ГАЗон Next", "Валдай", "Волга", "Газель Бизнес", "Газель Next", "Садко", "Соболь")
            "УАЗ" -> listOf("2206", "3160", "3303", "3909", "3962", "452", "469", "Pickup", "Буханка", "Патриот", "Профи", "Симбир", "Хантер")
            else -> listOf("Другая модель")
        }
    }

    val fuelTypes = listOf(
        "Бензин АИ-92",
        "Бензин АИ-95",
        "Бензин АИ-98",
        "Бензин АИ-100",
        "Дизель",
        "Дизель (евро)",
        "Пропан (ГБО)",
        "Метан (ГБО)",
        "Электричество",
        "Гибрид (бензин)",
        "Гибрид (дизель)"
    )
}