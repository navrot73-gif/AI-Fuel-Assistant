package com.navrot.aifuelassistant.ui.garage

import com.navrot.aifuelassistant.R
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandPhotoMapperTest {

    @Test
    fun `test Russian brand aliases`() {
        assertEquals(R.drawable.car_lada, BrandPhotoMapper.getStockPhotoResource("ВАЗ"))
        assertEquals(R.drawable.car_lada, BrandPhotoMapper.getStockPhotoResource("Лада"))
        assertEquals(R.drawable.car_lada, BrandPhotoMapper.getStockPhotoResource("лада"))
        assertEquals(R.drawable.car_lada, BrandPhotoMapper.getStockPhotoResource("Lada (ВАЗ)"))

        assertEquals(R.drawable.car_toyota, BrandPhotoMapper.getStockPhotoResource("Тойота"))
        assertEquals(R.drawable.car_kia, BrandPhotoMapper.getStockPhotoResource("Киа"))
        assertEquals(R.drawable.car_hyundai, BrandPhotoMapper.getStockPhotoResource("Хёндай"))
        assertEquals(R.drawable.car_hyundai, BrandPhotoMapper.getStockPhotoResource("хендай"))
        assertEquals(R.drawable.car_ford, BrandPhotoMapper.getStockPhotoResource("Форд"))
        assertEquals(R.drawable.car_volkswagen, BrandPhotoMapper.getStockPhotoResource("Фольксваген"))
        assertEquals(R.drawable.car_volkswagen, BrandPhotoMapper.getStockPhotoResource("фольцваген"))
        assertEquals(R.drawable.car_nissan, BrandPhotoMapper.getStockPhotoResource("Ниссан"))
        assertEquals(R.drawable.car_renault, BrandPhotoMapper.getStockPhotoResource("Рено"))
        assertEquals(R.drawable.car_skoda, BrandPhotoMapper.getStockPhotoResource("Шкода"))
        assertEquals(R.drawable.car_bmw, BrandPhotoMapper.getStockPhotoResource("БМВ"))
        assertEquals(R.drawable.car_mercedes, BrandPhotoMapper.getStockPhotoResource("Мерседес"))
        assertEquals(R.drawable.car_chevrolet, BrandPhotoMapper.getStockPhotoResource("Шевроле"))
        assertEquals(R.drawable.car_mitsubishi, BrandPhotoMapper.getStockPhotoResource("Митсубиси"))
        assertEquals(R.drawable.car_mazda, BrandPhotoMapper.getStockPhotoResource("Мазда"))
        assertEquals(R.drawable.car_honda, BrandPhotoMapper.getStockPhotoResource("Хонда"))
    }

    @Test
    fun `test English brand names`() {
        assertEquals(R.drawable.car_lada, BrandPhotoMapper.getStockPhotoResource("Lada"))
        assertEquals(R.drawable.car_toyota, BrandPhotoMapper.getStockPhotoResource("Toyota"))
        assertEquals(R.drawable.car_kia, BrandPhotoMapper.getStockPhotoResource("Kia"))
        assertEquals(R.drawable.car_hyundai, BrandPhotoMapper.getStockPhotoResource("Hyundai"))
        assertEquals(R.drawable.car_ford, BrandPhotoMapper.getStockPhotoResource("Ford"))
        assertEquals(R.drawable.car_volkswagen, BrandPhotoMapper.getStockPhotoResource("Volkswagen"))
        assertEquals(R.drawable.car_nissan, BrandPhotoMapper.getStockPhotoResource("Nissan"))
        assertEquals(R.drawable.car_renault, BrandPhotoMapper.getStockPhotoResource("Renault"))
        assertEquals(R.drawable.car_skoda, BrandPhotoMapper.getStockPhotoResource("Skoda"))
        assertEquals(R.drawable.car_bmw, BrandPhotoMapper.getStockPhotoResource("BMW"))
        assertEquals(R.drawable.car_mercedes, BrandPhotoMapper.getStockPhotoResource("Mercedes"))
        assertEquals(R.drawable.car_mercedes, BrandPhotoMapper.getStockPhotoResource("Mercedes-Benz"))
        assertEquals(R.drawable.car_chevrolet, BrandPhotoMapper.getStockPhotoResource("Chevrolet"))
        assertEquals(R.drawable.car_mitsubishi, BrandPhotoMapper.getStockPhotoResource("Mitsubishi"))
        assertEquals(R.drawable.car_mazda, BrandPhotoMapper.getStockPhotoResource("Mazda"))
        assertEquals(R.drawable.car_honda, BrandPhotoMapper.getStockPhotoResource("Honda"))
    }

    @Test
    fun `test whitespace and capitalization handling`() {
        assertEquals(R.drawable.car_toyota, BrandPhotoMapper.getStockPhotoResource("  TOYOTA  "))
        assertEquals(R.drawable.car_bmw, BrandPhotoMapper.getStockPhotoResource("bmw\n"))
    }

    @Test
    fun `test unknown brand returns generic car resource`() {
        assertEquals(R.drawable.car_generic, BrandPhotoMapper.getStockPhotoResource("Tesla"))
        assertEquals(R.drawable.car_generic, BrandPhotoMapper.getStockPhotoResource("Unknown"))
        assertEquals(R.drawable.car_generic, BrandPhotoMapper.getStockPhotoResource(""))
    }
}
