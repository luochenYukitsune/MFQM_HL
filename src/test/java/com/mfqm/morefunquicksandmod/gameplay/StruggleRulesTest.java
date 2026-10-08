package com.mfqm.morefunquicksandmod.gameplay;

import static com.mfqm.morefunquicksandmod.gameplay.StruggleRules.*;

/** Behavioral tests run with Java 21 and no Minecraft dependencies. */
public final class StruggleRulesTest {
    private static final Input REST = new Input(false, false, false);
    private static final Input KEY = new Input(false, false, true);
    private static int checks;

    public static void main(String[] args) {
        var first = step(State.initial(), 0, Medium.GLUE, KEY, 20);
        check(first.acceptedPress(), "first F edge is accepted");
        close(first.exhaustion(), .2, "accepted key spends appropriate exhaustion");
        var state = first.state();
        for (int tick = 1; tick <= 48; tick++) {
            var held = step(state, tick, Medium.GLUE, KEY, 20);
            check(!held.acceptedPress(), "held F never repeats, even after cooldown");
            close(held.exhaustion(), 0, "held F has no repeated hunger cost");
            state = held.state();
        }
        close(state.effort(), first.state().effort(), "held key adds no effort");
        var released = step(first.state(), 1, Medium.GLUE, REST, 20);
        var tooSoon = step(released.state(), 11, Medium.GLUE, KEY, 20);
        check(!tooSoon.acceptedPress(), "11-tick press is too early");
        var releaseAgain = step(tooSoon.state(), 12, Medium.GLUE, REST, 20);
        check(step(releaseAgain.state(), 13, Medium.GLUE, KEY, 20).acceptedPress(), "new edge after cooldown works");
        var boundaryRelease = step(first.state(), 1, Medium.GLUE, REST, 20);
        check(step(boundaryRelease.state(), 12, Medium.GLUE, KEY, 20).acceptedPress(), "12-tick boundary accepts");
        var duplicate = step(first.state(), 0, Medium.GLUE, REST, 20);
        check(duplicate.state().equals(first.state()) && !duplicate.active(), "duplicate tick cannot advance or release key");
        close(duplicate.sinkDelta(), 0, "duplicate emits no old depth increment");
        var oldTick = step(first.state(), 0, Medium.GLUE, KEY, 20);
        check(oldTick.state().equals(first.state()), "reordered tick cannot add progress");
        check(step(State.initial(), Long.MAX_VALUE - 12, Medium.GLUE, KEY, 20).acceptedPress(),
                "initial clock sentinel does not overflow at large world age");
        var ancient = step(State.initial(), Long.MAX_VALUE - 12, Medium.GLUE, KEY, 20);
        var ancientRelease = step(ancient.state(), Long.MAX_VALUE - 11, Medium.GLUE, REST, 20);
        check(step(ancientRelease.state(), Long.MAX_VALUE, Medium.GLUE, KEY, 20).acceptedPress(),
                "cooldown accepts boundary near maximum clock");

        state = State.initial();
        for (int tick = 0; tick < 600; tick++) {
            var quiet = step(state, tick, Medium.GLUE, REST, 20);
            close(quiet.sinkDelta(), 0, "motionless glue never sinks");
            state = quiet.state();
        }
        check(!state.glueSinkStarted(), "time alone does not start glue sinking");
        var moved = step(State.initial(), 0, Medium.GLUE, new Input(true, false, false), 20);
        check(moved.active() && moved.state().effort() > 0, "movement intent works even without displacement");
        close(moved.sinkDelta(), 0, "initial shallow movement does not sink immediately");
        var jumped = step(State.initial(), 0, Medium.GLUE, new Input(false, true, false), 20);
        check(jumped.state().effort() > moved.state().effort(), "jump attempt contributes stronger effort");
        var heldJump = step(jumped.state(), 1, Medium.GLUE, new Input(false, true, false), 20);
        close(heldJump.state().effort(), jumped.state().effort(), "held jump is not a new attempt each tick");

        state = State.initial();
        double depth = .15;
        for (int tick = 0; tick <= 60; tick++) {
            var result = step(state, tick, Medium.GLUE, tick % 12 == 0 ? KEY : REST, 20);
            depth += result.sinkDelta();
            state = result.state();
        }
        check(state.glueSinkStarted() && depth > .15, "repeated struggle eventually starts sinking");
        double stoppedDepth = depth;
        for (int tick = 61; tick < 120; tick++) {
            var stopped = step(state, tick, Medium.GLUE, REST, 20);
            depth += stopped.sinkDelta();
            state = stopped.state();
        }
        close(depth, stoppedDepth, "stopping struggle freezes depth without floating up");
        check(state.glueSinkStarted(), "stopping does not undo prior sink activation");
        check(state.adhesionScale(Medium.GLUE) > 0, "stopping leaves glue attached");

        int fedTicks = escapeTicks(20), hungryTicks = escapeTicks(5);
        check(fedTicks >= 300 && fedTicks <= 500, "fresh board releases after 15-25 seconds: " + fedTicks);
        check(hungryTicks > fedTicks && hungryTicks < 900, "hunger slows but never disables escape");
        var hungry = step(State.initial(), 0, Medium.BOARD, KEY, 5);
        var fed = step(State.initial(), 0, Medium.BOARD, KEY, 6);
        close(hungry.state().effort() / fed.state().effort(), .6, "hunger boundary lowers efficiency to 60 percent");
        close(hungry.exhaustion(), .2, "hungry player can still struggle and pays same action cost");
        close(fed.sinkDelta(), 0, "board struggle never creates a sinking delta");
        check(fed.adhesionReduction() > 0, "board struggle weakens adhesion");
        var other = step(State.initial(), 0, Medium.OTHER, KEY, 20);
        check(other.adhesionReduction() > 0 && other.sinkDelta() > 0, "other sticky media trade weaker adhesion for sinking");
        state = State.initial();
        int combinedTicks = -1;
        for (int tick = 0; tick <= 500; tick++) {
            state = step(state, tick, Medium.BOARD, new Input(true, false, tick % 12 == 0), 20).state();
            if (state.boardReleased(Medium.BOARD)) {
                combinedTicks = tick;
                break;
            }
        }
        check(combinedTicks >= 300 && combinedTicks <= 500, "walking plus F still respects board duration");
        check(!state.boardReleased(Medium.GLUE), "release threshold never treats glue as a released board");
        close(state.adhesionScale(Medium.BOARD), 0, "board cumulative adhesion can be fully weakened");
        check(state.adhesionScale(Medium.GLUE) > 0, "glue retains residual adhesion at maximum effort");

        var loss = evaluateBootLoss(State.initial(), Medium.GLUE, .05, .01);
        check(loss.lost() && loss.state().bootCheckMade(), "eligible pull can lose boots once");
        check(!evaluateBootLoss(loss.state(), Medium.GLUE, 1, 0).lost(), "repeat pull cannot reroll in one episode");
        var safe = evaluateBootLoss(State.initial(), Medium.BOARD, .05, .9);
        check(!safe.lost() && safe.state().bootCheckMade(), "unsuccessful roll is still consumed");
        var excluded = evaluateBootLoss(State.initial(), Medium.OTHER, 1, 0);
        check(!excluded.lost() && !excluded.state().bootCheckMade(), "other media do not lose boots");
        check(markBootChecked(loss.state()) == loss.state(), "consumed boot marker is idempotent");
        check(evaluateBootLoss(State.initial(), Medium.GLUE, 1, 0).lost(), "new episode restores exactly one roll");
        check(!State.initial().glueSinkStarted() && State.initial().effort() == 0,
                "new episode does not carry old glue activation or progress");
        check(!shouldLoseBoot(State.initial(), Medium.GLUE, 0, 0), "zero probability cannot lose boots");
        rejects(() -> step(State.initial(), -1, Medium.GLUE, REST, 20), "negative tick");
        rejects(() -> step(State.initial(), 0, Medium.GLUE, REST, 21), "invalid hunger");
        rejects(() -> evaluateBootLoss(State.initial(), Medium.GLUE, Double.NaN, 0), "NaN boot probability");
        rejects(() -> evaluateBootLoss(State.initial(), Medium.GLUE, .05, 1), "out-of-range boot sample");
        System.out.println("StruggleRules: " + checks + " assertions passed; board fed=" + fedTicks / 20.0
                + "s hungry=" + hungryTicks / 20.0 + "s");
    }

    private static int escapeTicks(int food) {
        State state = State.initial();
        for (int tick = 0; tick <= 1000; tick++) {
            var result = step(state, tick, Medium.BOARD, tick % 12 == 0 ? KEY : REST, food);
            state = result.state();
            if (state.boardReleased(Medium.BOARD)) return tick;
        }
        throw new AssertionError("board never released");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message + ": " + actual + " != " + expected);
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("accepted " + message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }
}
