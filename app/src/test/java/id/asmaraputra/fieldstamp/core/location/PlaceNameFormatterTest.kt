package id.asmaraputra.fieldstamp.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceNameFormatterTest {
    @Test fun buildsStreetToProvince() {
        assertEquals(
            "Jalan Raya Bogor No. 12, Kramat Jati, Kecamatan Kramat Jati, Kota Jakarta Timur, Daerah Khusus Ibukota Jakarta",
            PlaceNameFormatter.format("Jalan Raya Bogor", "12", "Kramat Jati", "Kecamatan Kramat Jati", "Kota Jakarta Timur", "Daerah Khusus Ibukota Jakarta"),
        )
    }

    @Test fun dropsDuplicatesAndPlusCodes() {
        assertEquals(
            "Sukamaju, Kabupaten Bogor, Jawa Barat",
            PlaceNameFormatter.format("7Q5V+XX", null, "Sukamaju", "sukamaju", "Kabupaten Bogor", "Jawa Barat"),
        )
    }

    @Test fun skipsUnnamedRoads() {
        assertEquals("Cibinong, Jawa Barat", PlaceNameFormatter.format("Unnamed Road", null, null, "Cibinong", null, "Jawa Barat"))
    }

    @Test fun fallsBackToFormattedLineWithoutPostalCodeOrCountry() {
        assertEquals(
            "Gg. Melati, Sukamaju",
            PlaceNameFormatter.format(null, null, null, null, null, null, fallbackLine = "7Q5V+XX Gg. Melati, Sukamaju 16911, Indonesia"),
        )
    }

    @Test fun returnsNullWhenNothingUsable() {
        assertNull(PlaceNameFormatter.format(null, null, null, null, null, null, fallbackLine = "Indonesia"))
    }

    @Test fun capsLength() {
        val long = "x".repeat(400)
        assertEquals(PlaceNameFormatter.MAX_LENGTH, PlaceNameFormatter.format(long, null, "a", null, null, null)!!.length)
    }
}
