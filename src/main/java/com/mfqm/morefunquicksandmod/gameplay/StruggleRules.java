package com.mfqm.morefunquicksandmod.gameplay;

import java.util.Objects;

/**
 * Server-authoritative rules for one continuous trapping episode, independent of Minecraft.
 * Call {@link #step} once per tick with actual movement intent, not measured displacement.
 * A network F-press may be represented by keyDown for one tick followed by false; clients must
 * send real rising edges rather than operating-system key repeats. Animation may last twelve
 * ticks, but a key press emits its sinking cost only once, on the accepted tick.
 */
public final class StruggleRules {
    public static final int PRESS_COOLDOWN_TICKS = 12;
    public static final double BOARD_RELEASE_EFFORT = 34;
    public static final double GLUE_SINK_START_EFFORT = 4;
    public static final double PRESS_EXHAUSTION = .2;

    private StruggleRules() {}

    public enum Medium { GLUE, BOARD, OTHER }

    /** jumping and keyDown use rising edges; moving contributes a small amount each tick. */
    public record Input(boolean moving, boolean jumping, boolean keyDown) {}

    /** Reset to initial only when the trapping episode actually ends, not when the actor rests. */
    public record State(long lastProcessedTick, long lastAcceptedTick, double effort,
                        boolean glueSinkStarted, boolean keyDown, boolean jumpDown, boolean bootCheckMade) {
        public State {
            if (!validClock(lastProcessedTick) || !validClock(lastAcceptedTick)
                    || (lastAcceptedTick != Long.MIN_VALUE && lastAcceptedTick > lastProcessedTick)
                    || !Double.isFinite(effort) || effort < 0 || effort > BOARD_RELEASE_EFFORT) {
                throw new IllegalArgumentException("Invalid episode state");
            }
        }

        public static State initial() { return new State(Long.MIN_VALUE, Long.MIN_VALUE, 0, false, false, false, false); }

        public boolean boardReleased(Medium medium) {
            return Objects.requireNonNull(medium) == Medium.BOARD && effort >= BOARD_RELEASE_EFFORT;
        }

        /** Cumulative fraction of initial adhesion; glue and other media keep residual attachment. */
        public double adhesionScale(Medium medium) {
            Objects.requireNonNull(medium);
            double fraction = Math.max(0, 1 - effort / BOARD_RELEASE_EFFORT);
            return medium == Medium.BOARD ? fraction : Math.max(.2, fraction);
        }
    }

    /** sinkDelta and adhesionReduction belong only to this tick; never reapply old deltas. */
    public record Result(State state, boolean acceptedPress, boolean active,
                         double adhesionReduction, double sinkDelta, double exhaustion) {}

    public record BootDecision(State state, boolean lost) {}

    public static Result step(State state, long tick, Medium medium, Input input, int foodLevel) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(medium);
        Objects.requireNonNull(input);
        if (tick < 0 || foodLevel < 0 || foodLevel > 20) {
            throw new IllegalArgumentException("Expected nonnegative tick and food level 0..20");
        }
        // Duplicate or reordered delivery has no effect, including no fabricated key release.
        if (tick <= state.lastProcessedTick()) return new Result(state, false, false, 0, 0, 0);

        boolean ready = state.lastAcceptedTick() == Long.MIN_VALUE
                || tick - state.lastAcceptedTick() >= PRESS_COOLDOWN_TICKS;
        boolean press = input.keyDown() && !state.keyDown() && ready;
        boolean jump = input.jumping() && !state.jumpDown() && ready && !press;
        double efficiency = foodLevel < 6 ? .6 : 1;
        double effortDelta = ((press ? 1 : 0) + (jump ? .25 : 0) + (input.moving() ? .008 : 0)) * efficiency;
        double effort = Math.min(BOARD_RELEASE_EFFORT, state.effort() + effortDelta);
        boolean sinkStarted = state.glueSinkStarted() || medium == Medium.GLUE && effort >= GLUE_SINK_START_EFFORT;
        boolean active = input.moving() || press || jump;
        State next = new State(tick, press || jump ? tick : state.lastAcceptedTick(), effort, sinkStarted,
                input.keyDown(), input.jumping(), state.bootCheckMade());

        double sink = 0;
        if (active && medium != Medium.BOARD && (medium != Medium.GLUE || sinkStarted)) {
            sink = ((press ? .025 : 0) + (jump ? .01 : 0) + (input.moving() ? .0006 : 0)) * efficiency;
        }
        return new Result(next, press, active,
                state.adhesionScale(medium) - next.adhesionScale(medium), sink,
                press ? PRESS_EXHAUSTION : 0);
    }

    /** Marks one boot-loss roll consumed, including unsuccessful rolls. */
    public static State markBootChecked(State state) {
        Objects.requireNonNull(state);
        if (state.bootCheckMade()) return state;
        return new State(state.lastProcessedTick(), state.lastAcceptedTick(), state.effort(),
                state.glueSinkStarted(), state.keyDown(), state.jumpDown(), true);
    }

    /** Pure roll predicate. Call only for a real forceful foot pull or broken bond in glue/board. */
    public static boolean shouldLoseBoot(State state, Medium medium, double probability, double sample) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(medium);
        if (!Double.isFinite(probability) || probability < 0 || probability > 1
                || !Double.isFinite(sample) || sample < 0 || sample >= 1) {
            throw new IllegalArgumentException("Expected probability 0..1 and sample 0..<1");
        }
        return medium != Medium.OTHER && !state.bootCheckMade() && sample < probability;
    }

    /** Atomically returns the episode marker and result; caller performs actual equipment transfer. */
    public static BootDecision evaluateBootLoss(State state, Medium medium, double probability, double sample) {
        boolean lost = shouldLoseBoot(state, medium, probability, sample);
        return new BootDecision(medium == Medium.OTHER ? state : markBootChecked(state), lost);
    }

    private static boolean validClock(long value) {
        return value == Long.MIN_VALUE || value >= 0;
    }
}
