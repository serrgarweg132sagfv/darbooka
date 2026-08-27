package com.darbukapractice.app.utils
import org.junit.Assert.assertEquals
import org.junit.Test
class JalaliTest {
 @Test fun epochFormatsToJalali(){ assertEquals("1400/10/10", Jalali.format(java.time.LocalDate.of(2022,1,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())) }
}
