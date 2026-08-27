package com.navrot.aifuelassistant.ui.garage

import androidx.annotation.DrawableRes
import com.navrot.aifuelassistant.R

object BrandPhotoMapper {
    /**
     * Возвращает ресурс стокового фото по бренду авто.
     * Если бренд не найден — возвращает универсальную иконку авто.
     */
    @DrawableRes
    fun getStockPhotoResource(brand: String): Int {
        return when (brand.lowercase().trim()) {
            "ваз", "lada", "лада", "lada (ваз)" -> R.drawable.car_lada
            "toyota", "тойота" -> R.drawable.car_toyota
            "kia", "киа" -> R.drawable.car_kia
            "hyundai", "хёндай", "хендай" -> R.drawable.car_hyundai
            "ford", "форд" -> R.drawable.car_ford
            "volkswagen", "фольксваген", "фольцваген" -> R.drawable.car_volkswagen
            "nissan", "ниссан" -> R.drawable.car_nissan
            "renault", "рено" -> R.drawable.car_renault
            "skoda", "шкода" -> R.drawable.car_skoda
            "bmw", "бмв" -> R.drawable.car_bmw
            "mercedes", "мерседес", "mercedes-benz" -> R.drawable.car_mercedes
            "chevrolet", "шевроле" -> R.drawable.car_chevrolet
            "mitsubishi", "митсубиси" -> R.drawable.car_mitsubishi
            "mazda", "мазда" -> R.drawable.car_mazda
            "honda", "хонда" -> R.drawable.car_honda
            else -> R.drawable.car_generic
        }
    }
}
