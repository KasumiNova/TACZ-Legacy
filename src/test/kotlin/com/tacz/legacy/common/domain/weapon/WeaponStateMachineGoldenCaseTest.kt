package com.tacz.legacy.common.domain.weapon

import com.tacz.legacy.common.application.testing.GoldenCase
import com.tacz.legacy.common.application.testing.GoldenCaseRunner
import org.junit.Test

public class WeaponStateMachineGoldenCaseTest {

    @Test
    public fun `golden cases should keep deterministic weapon transitions`() {
        val runner = GoldenCaseRunner<WeaponScenario, WeaponOutcome>()
        val cases = listOf(
            GoldenCase(
                id = "auto-fire-then-reload",
                input = WeaponScenario(
                    spec = WeaponSpec(
                        magazineSize = 3,
                        roundsPerMinute = 600,
                        reloadTicks = 2,
                        fireMode = WeaponFireMode.AUTO
                    ),
                    initial = WeaponSnapshot(ammoInMagazine = 3, ammoReserve = 3),
                    inputs = listOf(
                        WeaponInput.TriggerPressed,
                        WeaponInput.Tick,
                        WeaponInput.Tick,
                        WeaponInput.Tick,
                        WeaponInput.Tick,
                        WeaponInput.TriggerReleased,
                        WeaponInput.ReloadPressed,
                        WeaponInput.Tick,
                        WeaponInput.Tick
                    )
                ),
                expected = WeaponOutcome(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 3,
                    ammoReserve = 0,
                    totalShotsFired = 3
                )
            ),
            GoldenCase(
                id = "semi-press-release-discipline",
                input = WeaponScenario(
                    spec = WeaponSpec(
                        magazineSize = 6,
                        roundsPerMinute = 1200,
                        reloadTicks = 2,
                        fireMode = WeaponFireMode.SEMI
                    ),
                    initial = WeaponSnapshot(ammoInMagazine = 6, ammoReserve = 0),
                    inputs = listOf(
                        WeaponInput.TriggerPressed,
                        WeaponInput.TriggerPressed,
                        WeaponInput.Tick,
                        WeaponInput.TriggerReleased,
                        WeaponInput.TriggerPressed,
                        WeaponInput.TriggerReleased
                    )
                ),
                expected = WeaponOutcome(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 4,
                    ammoReserve = 0,
                    totalShotsFired = 2
                )
            ),
            GoldenCase(
                id = "reload-with-limited-reserve",
                input = WeaponScenario(
                    spec = WeaponSpec(
                        magazineSize = 10,
                        roundsPerMinute = 600,
                        reloadTicks = 1,
                        fireMode = WeaponFireMode.AUTO
                    ),
                    initial = WeaponSnapshot(ammoInMagazine = 2, ammoReserve = 3),
                    inputs = listOf(
                        WeaponInput.ReloadPressed,
                        WeaponInput.Tick
                    )
                ),
                expected = WeaponOutcome(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 5,
                    ammoReserve = 0,
                    totalShotsFired = 0
                )
            ),
            GoldenCase(
                id = "burst-three-rounds-and-lock",
                input = WeaponScenario(
                    spec = WeaponSpec(
                        magazineSize = 9,
                        roundsPerMinute = 1200,
                        reloadTicks = 2,
                        fireMode = WeaponFireMode.BURST,
                        burstSize = 3
                    ),
                    initial = WeaponSnapshot(ammoInMagazine = 9, ammoReserve = 0),
                    inputs = listOf(
                        WeaponInput.TriggerPressed,
                        WeaponInput.Tick,
                        WeaponInput.Tick,
                        WeaponInput.Tick,
                        WeaponInput.TriggerReleased,
                        WeaponInput.TriggerPressed
                    )
                ),
                expected = WeaponOutcome(
                    state = WeaponState.FIRING,
                    ammoInMagazine = 5,
                    ammoReserve = 0,
                    totalShotsFired = 4
                )
            )
        )

        runner.assertAll(cases) { scenario -> execute(scenario) }
    }

    private fun execute(scenario: WeaponScenario): WeaponOutcome {
        val machine = WeaponStateMachine(scenario.spec, scenario.initial)
        scenario.inputs.forEach { input -> machine.dispatch(input) }

        val snapshot = machine.snapshot()
        return WeaponOutcome(
            state = snapshot.state,
            ammoInMagazine = snapshot.ammoInMagazine,
            ammoReserve = snapshot.ammoReserve,
            totalShotsFired = snapshot.totalShotsFired
        )
    }

    private data class WeaponScenario(
        val spec: WeaponSpec,
        val initial: WeaponSnapshot,
        val inputs: List<WeaponInput>
    )

    private data class WeaponOutcome(
        val state: WeaponState,
        val ammoInMagazine: Int,
        val ammoReserve: Int,
        val totalShotsFired: Int
    )

}