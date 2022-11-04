package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.error.InputValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class OidTest {

    @Test
    fun emptyStringIsNotAnOid() {
        assertThrows<InputValidationException> { Oid<Any>("") }
    }

    @Test
    fun lettersNotAllowedInOid() {
        assertThrows<InputValidationException> { Oid<Any>("abc.def.ghj") }
    }

    @Test
    fun oidCantStartOrEndInDot() {
        assertThrows<InputValidationException> { Oid<Any>(".1.23.4.56") }
        assertThrows<InputValidationException> { Oid<Any>("1.23.34.5.") }
    }

    @Test
    fun numbersAndDotsAreAllowedInOid() {
        assertDoesNotThrow { Oid<Any>("1.2.3") }
        assertDoesNotThrow { Oid<Any>("1.23.456") }
        assertDoesNotThrow { Oid<Any>("1.23.456.7890") }
    }
}
