package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.tracklayout.DesignState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtDesignV1Test {

    @Test
    fun `Design states map to their Finnish API values`() {
        assertEquals(FI_DESIGN_ACTIVE, ExtDesignStateV1.of(DesignState.ACTIVE).jsonValue())
        assertEquals(FI_DESIGN_DELETED, ExtDesignStateV1.of(DesignState.DELETED).jsonValue())
        assertEquals(FI_DESIGN_COMPLETED, ExtDesignStateV1.of(DesignState.COMPLETED).jsonValue())
    }
}
