package fi.fta.geoviite.infra.geometry

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class PlanApplicabilityTest {

    @Test
    fun `UNKNOWN quality gives STATISTICS regardless of decision phase`() {
        PlanDecisionPhase.entries.forEach { phase ->
            assertEquals(PlanApplicability.STATISTICS, computePlanApplicability(PlanQuality.UNKNOWN, phase))
        }
    }

    @Test
    fun `PLAN quality with UNKNOWN decision phase gives STATISTICS`() {
        assertEquals(
            PlanApplicability.STATISTICS,
            computePlanApplicability(PlanQuality.PLAN, PlanDecisionPhase.UNKNOWN),
        )
    }

    @Test
    fun `UNRELIABLE_PLAN quality always gives STATISTICS regardless of decision phase`() {
        PlanDecisionPhase.entries.forEach { phase ->
            assertEquals(PlanApplicability.STATISTICS, computePlanApplicability(PlanQuality.UNRELIABLE_PLAN, phase))
        }
    }

    @Test
    fun `PLAN quality with IN_USE gives MAINTENANCE`() {
        assertEquals(
            PlanApplicability.MAINTENANCE,
            computePlanApplicability(PlanQuality.PLAN, PlanDecisionPhase.IN_USE),
        )
    }

    @Test
    fun `PLAN quality with OUTDATED gives STATISTICS`() {
        assertEquals(
            PlanApplicability.STATISTICS,
            computePlanApplicability(PlanQuality.PLAN, PlanDecisionPhase.OUTDATED),
        )
    }

    @Test
    fun `PLAN quality with APPROVED_PLAN or UNDER_CONSTRUCTION gives PLANNING`() {
        assertEquals(
            PlanApplicability.PLANNING,
            computePlanApplicability(PlanQuality.PLAN, PlanDecisionPhase.APPROVED_PLAN),
        )
        assertEquals(
            PlanApplicability.PLANNING,
            computePlanApplicability(PlanQuality.PLAN, PlanDecisionPhase.UNDER_CONSTRUCTION),
        )
    }
}
