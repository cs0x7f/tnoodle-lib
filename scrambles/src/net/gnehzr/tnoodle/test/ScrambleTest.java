package net.gnehzr.tnoodle.test;

import static net.gnehzr.tnoodle.utils.GwtSafeUtils.azzert;
import static net.gnehzr.tnoodle.utils.GwtSafeUtils.azzertEquals;
import static net.gnehzr.tnoodle.utils.GwtSafeUtils.azzertSame;
import static net.gnehzr.tnoodle.utils.GwtSafeUtils.choose;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Random;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gnehzr.tnoodle.scrambles.AlgorithmBuilder;
import net.gnehzr.tnoodle.scrambles.AlgorithmBuilder.MungingMode;
import net.gnehzr.tnoodle.scrambles.InvalidMoveException;
import net.gnehzr.tnoodle.scrambles.InvalidScrambleException;
import net.gnehzr.tnoodle.scrambles.Puzzle;
import net.gnehzr.tnoodle.scrambles.PuzzlePlugins;
import net.gnehzr.tnoodle.scrambles.Puzzle.PuzzleState;
import net.gnehzr.tnoodle.scrambles.ScrambleCacher;
import net.gnehzr.tnoodle.scrambles.ScrambleCacherListener;
import net.gnehzr.tnoodle.utils.BadLazyClassDescriptionException;
import net.gnehzr.tnoodle.utils.LazyInstantiatorException;
import net.gnehzr.tnoodle.utils.LazyInstantiator;
import net.gnehzr.tnoodle.utils.TimedLogRecordStart;
import net.gnehzr.tnoodle.utils.Utils;
import puzzle.ClockPuzzle;
import puzzle.ClockPuzzle.ClockState;
import puzzle.CubePuzzle;
import puzzle.CubePuzzle.CubeState;
import puzzle.PyraminxPuzzle;
import puzzle.PyraminxPuzzle.PyraminxState;
import puzzle.PyraminxSolver;
import puzzle.PyraminxSolver.PyraminxSolverState;
import puzzle.MegaminxPuzzle;
import puzzle.TwoByTwoSolver;
import puzzle.TwoByTwoSolver.TwoByTwoState;
import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.Exportable;

public class ScrambleTest {
    private static final Logger l = Logger.getLogger(Puzzle.class.getName());
    
    private static final Random r = Utils.getSeededRandom();
    
    static class LockHolder extends Thread {
        public LockHolder() {
            setDaemon(true);
        }

        private Object o;
        public void setObjectToLock(Object o) {
            synchronized(this) {
                this.o = o;
                if(isAlive()) {
                    notify();
                } else {
                    start();
                }
            }
            try {
                Thread.sleep(100); // give the locker thread a chance to grab the lock
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
        @Override
        public synchronized void run() {
            while(o != null) {
                synchronized(o) {
                    System.out.println("GOT LOCK " + o);
                    Object locked = o;
                    while(o == locked) {
                        try {
                            wait();
                        } catch (InterruptedException e) {}
                    }
                }
            }
        }
    }

    public static void testScrambleFiltering() throws BadLazyClassDescriptionException, LazyInstantiatorException, InvalidScrambleException, IOException {
        System.out.println("Testing scramble filtering");

        int SCRAMBLE_COUNT = 10;

        SortedMap<String, LazyInstantiator<Puzzle>> lazyScramblers = PuzzlePlugins.getScramblers();
        for(String puzzle : lazyScramblers.keySet()) {
            LazyInstantiator<Puzzle> lazyScrambler = lazyScramblers.get(puzzle);
            final Puzzle scrambler = lazyScrambler.cachedInstance();
            for(int count = 0; count < SCRAMBLE_COUNT; count++){
                PuzzleState state = scrambler.getSolvedState().applyAlgorithm(scrambler.generateWcaScramble(r));
                azzertSame(state.solveIn(scrambler.getWcaMinScrambleDistance() - 1), null);
            }
        }
    }

    public static void main(String[] args) throws BadLazyClassDescriptionException, LazyInstantiatorException, InvalidScrambleException, InvalidMoveException, IOException {

        System.out.println("Testing names.");
        testNames();

        benchmarking();

        System.out.println("Testing specific Puzzle issues.");
        testClockPuzzle();
        testCubePuzzle();
        testPyraConverter();
        testMega();

        System.out.println("Testing solveIn method");
        testSolveIn();

        testScrambleFiltering();

        testThreads();
    }

    private static void testSolveIn() throws InvalidScrambleException, BadLazyClassDescriptionException, LazyInstantiatorException, IOException {
        int SCRAMBLE_COUNT = 10;
        int SCRAMBLE_LENGTH = 4;

        SortedMap<String, LazyInstantiator<Puzzle>> lazyScramblers = PuzzlePlugins.getScramblers();

        for(String puzzle : lazyScramblers.keySet()) {
            LazyInstantiator<Puzzle> lazyScrambler = lazyScramblers.get(puzzle);
            final Puzzle scrambler = lazyScrambler.cachedInstance();

            System.out.println("Testing " + puzzle);
            
            // Test solving the solved state
            String solution = scrambler.getSolvedState().solveIn(0);
            azzert("".equals(solution));

            for(int count = 0; count < SCRAMBLE_COUNT; count++) {
                System.out.print("Scramble ["+(count+1)+"/"+SCRAMBLE_COUNT+"]: ");
                PuzzleState state = scrambler.getSolvedState();
                for(int i = 0; i < SCRAMBLE_LENGTH; i++){
                    HashMap<String, ? extends PuzzleState> successors = state.getSuccessors();
                    String move = choose(r, successors.keySet());
                    System.out.print(" "+move);
                    state = successors.get(move);
                }
                System.out.print("...");
                solution = state.solveIn(SCRAMBLE_LENGTH);
                azzert(solution != null, "Puzzle "+scrambler.getShortName()+" solveIn method failed!");
                System.out.println("Found: "+solution);
                state = state.applyAlgorithm(solution);
                azzert(state.isSolved(), "Solution was not correct");
            }
        }
    }

    private static void testThreads() throws LazyInstantiatorException, InvalidScrambleException, BadLazyClassDescriptionException, IOException {
        LockHolder lh = new LockHolder();

        int SCRAMBLE_COUNT = 10;
        boolean drawScramble = true;

        SortedMap<String, LazyInstantiator<Puzzle>> lazyScramblers = PuzzlePlugins.getScramblers();

        for(String puzzle : lazyScramblers.keySet()) {
            LazyInstantiator<Puzzle> lazyScrambler = lazyScramblers.get(puzzle);
            final Puzzle scrambler = lazyScrambler.cachedInstance();

            System.out.println("Testing " + puzzle);

            // It's easy to get this wrong (read about Arrays.hashCode vs Arrays.deepHashCode).
            // This is just a sanity check.
            azzert(scrambler.getSolvedState().hashCode() == scrambler.getSolvedState().hashCode());

            // Generating a scramble
            System.out.println("Generating a " + puzzle + " scramble");
            String scramble;
            lh.setObjectToLock(scrambler);
            scramble = scrambler.generateScramble();

            // Drawing that scramble
            System.out.println("Drawing " + scramble);
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            Dimension size = new Dimension(image.getWidth(), image.getHeight());
            scrambler.drawScramble(image.createGraphics(), size, scramble, null);

            // Scramblers should support "null" as the empty scramble
            scrambler.drawScramble(image.createGraphics(), size, null, null);

            System.out.println("Generating & drawing 2 sets of " + SCRAMBLE_COUNT + " scrambles simultaneously." +
                                " This is meant to shake out threading problems in scramblers.");
            final Object[] o = new Object[0];
            ScrambleCacherListener cacherStopper = new ScrambleCacherListener() {
                @Override
                public void scrambleCacheUpdated(ScrambleCacher src) {
                    System.out.println(Thread.currentThread() + " " + src.getAvailableCount() + " / " + src.getCacheSize());
                    if(src.getAvailableCount() == src.getCacheSize()) {
                        src.stop();
                        synchronized(o) {
                            o.notify();
                        }
                    }
                }
            };
            ScrambleCacher c1 = new ScrambleCacher(scrambler, SCRAMBLE_COUNT, drawScramble, cacherStopper);
            ScrambleCacher c2 = new ScrambleCacher(scrambler, SCRAMBLE_COUNT, drawScramble, cacherStopper);
            c1.addScrambleCacherListener(cacherStopper);
            c2.addScrambleCacherListener(cacherStopper);
            while(c1.isRunning() || c2.isRunning()) {
                synchronized(o) {
                    try {
                        o.wait();
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
        lh.setObjectToLock(null);
        System.out.println("\nTest passed!");
    }

    private static void testNames() throws BadLazyClassDescriptionException, LazyInstantiatorException, IOException {
        SortedMap<String, LazyInstantiator<Puzzle>> lazyScramblers = PuzzlePlugins.getScramblers();

        // Check that the names by which the scramblers refer to themselves
        // is the same as the names by which we refer to them in the plugin definitions file.
        for(String shortName : lazyScramblers.keySet()) {
            String longName = PuzzlePlugins.getScramblerLongName(shortName);
            LazyInstantiator<Puzzle> lazyScrambler = lazyScramblers.get(shortName);
            Puzzle scrambler = lazyScrambler.cachedInstance();
            
            azzertEquals(shortName, scrambler.getShortName());
            azzertEquals(longName, scrambler.getLongName());

            System.out.println(Exportable.class + " isAssignableFrom " + scrambler.getClass());
            azzert(Exportable.class.isAssignableFrom(scrambler.getClass()));
            Annotation[] annotations = scrambler.getClass().getAnnotations();
            boolean foundExport = false;
            for(Annotation annotation : annotations) {
                if(Export.class.isAssignableFrom(annotation.annotationType())) {
                    foundExport = true;
                    break;
                }
            }
            azzert(foundExport);
        }
    }

    private static void testClockPuzzle() throws InvalidScrambleException, InvalidMoveException {
        ClockPuzzle clock = new ClockPuzzle();
        ClockState state = (ClockState)clock.getSolvedState();
        state = (ClockState)state.applyAlgorithm("ALL2+ y2 ALL1-"); // This scramble is breaking the solveIn method...
        String solution = state.solveIn(3);
        if(solution == null) {
            System.out.println("No solution");
        } else {
            System.out.println(solution);
        }
    }
    
    private static void testCubePuzzle() throws InvalidScrambleException, InvalidMoveException {
        testCubeNormalization();
        testTwosConverter();
        testTwosSolver();
    }

    private static void testCubeNormalization() throws InvalidScrambleException, InvalidMoveException {
        CubePuzzle fours = new CubePuzzle(4);
        CubeState solved = fours.getSolvedState();

        CubeState state = (CubeState) solved.applyAlgorithm("Rw Lw'");
        azzertEquals(state, solved);
        azzertEquals(state.hashCode(), solved.hashCode());
        
        state = (CubeState) solved.applyAlgorithm("Uw Dw'");
        azzertEquals(state, solved);
        
        CubePuzzle threes = new CubePuzzle(3);

        AlgorithmBuilder ab3 = new AlgorithmBuilder(threes, MungingMode.MUNGE_REDUNDANT_MOVES);
        ab3.appendAlgorithm("D2 U' L2 B2 F2 D B2 U' B2 F D' F U' R F2 L2 D' B D F'");
        azzertEquals(ab3.toString(), "D2 U' L2 B2 F2 D B2 U' B2 F D' F U' R F2 L2 D' B D F'");
        
        for(int depth = 0; depth < 100; depth++) {
            state = choose(r, state.getSuccessors().values());
            azzertEquals(state, state.applyAlgorithm("Uw Dw'"));
        }
    }

    private static void testTwosConverter() throws InvalidMoveException {
        int orient = 0;
        int permute = 0;

        int MOVE_R = 3;
        orient = TwoByTwoSolver.moveOrient[orient][MOVE_R];
        permute = TwoByTwoSolver.movePerm[permute][MOVE_R];

        CubePuzzle twos = new CubePuzzle(2);
        CubeState state = (CubeState) twos.getSolvedState().apply("R");
        TwoByTwoState twoByTwoState = state.toTwoByTwoState();

        azzertEquals(twoByTwoState.orientation, orient);
        azzertEquals(twoByTwoState.permutation, permute);

        TwoByTwoSolver twoByTwoSolver = new TwoByTwoSolver();
        azzert(twoByTwoSolver.solveIn(twoByTwoState, 1).equals("R'"));

        int MOVE_R_PRIME = 5;
        orient = TwoByTwoSolver.moveOrient[orient][MOVE_R_PRIME];
        permute = TwoByTwoSolver.movePerm[permute][MOVE_R_PRIME];
        azzert(orient == 0);
        azzert(permute == 0);
    }

    private static void testTwosSolver() throws InvalidScrambleException {
        CubePuzzle twos = new CubePuzzle(2);
        CubeState state = (CubeState) twos.getSolvedState();
        String solution = state.solveIn(0);
        azzert(solution.equals(""));

        String scrambleString = "R2 B2 F2";
        try {
            state = (CubeState) state.applyAlgorithm(scrambleString);
        } catch (InvalidScrambleException e) {
            azzert(false, e);
        }

        solution = state.solveIn(1);
        azzert(solution != null);
        state = (CubeState) state.applyAlgorithm(solution);
        azzert(state.isSolved());
    }

    private static void testPyraConverter() throws InvalidMoveException {
        int SCRAMBLE_COUNT = 1000;
        int SCRAMBLE_LENGTH = 20;

        int edgePerm = 0;
        int edgeOrient = 0;
        int cornerOrient = 0;
        int tips = 0;
        final String[] moveToString = {"U", "U'", "L", "L'", "R", "R'", "B", "B'"};

        PyraminxPuzzle pyra = new PyraminxPuzzle();
        PyraminxState state = (PyraminxState) pyra.getSolvedState();
        PyraminxSolverState sstate = state.toPyraminxSolverState();
        azzertEquals(sstate.edgePerm, edgePerm);
        azzertEquals(sstate.edgeOrient, edgeOrient);
        azzertEquals(sstate.cornerOrient, cornerOrient);
        azzertEquals(sstate.tips, tips);

        for (int i = 0; i < SCRAMBLE_COUNT; i++){
            System.out.println(" Scramble ["+i+"/"+SCRAMBLE_COUNT+"]");
            edgePerm = 0;
            edgeOrient = 0;
            cornerOrient = 0;
            state = (PyraminxState) pyra.getSolvedState();
            for (int j = 0; j < SCRAMBLE_LENGTH; j++){
                int move = r.nextInt(moveToString.length);
                edgePerm = PyraminxSolver.moveEdgePerm[edgePerm][move];
                edgeOrient = PyraminxSolver.moveEdgeOrient[edgeOrient][move];
                cornerOrient = PyraminxSolver.moveCornerOrient[cornerOrient][move];
                state = (PyraminxState) state.apply(moveToString[move]);
            }
            sstate = state.toPyraminxSolverState();

            azzertEquals(sstate.edgePerm, edgePerm);
            azzertEquals(sstate.edgeOrient, edgeOrient);
            azzertEquals(sstate.cornerOrient, cornerOrient);
        }
        System.out.println("");
    }

    public static void testMega() throws InvalidScrambleException {
        MegaminxPuzzle megaminx = new MegaminxPuzzle();
        PuzzleState solved = megaminx.getSolvedState();

        String spinL = "R++ L2'";
        String spinU = "D++ U2'";
        PuzzleState state = solved.applyAlgorithm(spinL).applyAlgorithm(spinU).applyAlgorithm(spinU).applyAlgorithm(spinL).applyAlgorithm(spinL).applyAlgorithm(spinL);
        state = state.applyAlgorithm(spinU);
        azzertEquals(state, solved);
    }

    private static void benchmarking() throws BadLazyClassDescriptionException, LazyInstantiatorException, InvalidScrambleException, IOException {

        // Analyze the 3x3x3 solver.
        int THREE_BY_THREE_SCRAMBLE_COUNT = 100;
        int THREE_BY_THREE_MAX_SCRAMBLE_LENGTH = 21;
        int THREE_BY_THREE_TIMEMIN = 0; //milliseconds
        int THREE_BY_THREE_TIMEOUT = 5*1000; //milliseconds

        cs.min2phase.Search threeSolver = new cs.min2phase.Search();
        cs.min2phase.Tools.init();
        TimedLogRecordStart start = new TimedLogRecordStart(Level.INFO, "Searching for " + THREE_BY_THREE_SCRAMBLE_COUNT + " random 3x3x3 cubes in less that " + THREE_BY_THREE_MAX_SCRAMBLE_LENGTH + " moves");
        l.log(start);
        for(int i = 0; i < THREE_BY_THREE_SCRAMBLE_COUNT; i++){
            threeSolver.solution(cs.min2phase.Tools.randomCube(r), THREE_BY_THREE_MAX_SCRAMBLE_LENGTH, THREE_BY_THREE_TIMEOUT, THREE_BY_THREE_TIMEMIN, cs.min2phase.Search.INVERSE_SOLUTION);
        }
        l.log(start.finishedNow());


        // How long does it takes to test if a puzzle is solvable in <= 1 move?
        int SCRAMBLE_COUNT = 100;
        SortedMap<String, LazyInstantiator<Puzzle>> lazyScramblers = PuzzlePlugins.getScramblers();
        
        for(String puzzle : lazyScramblers.keySet()) {
            LazyInstantiator<Puzzle> lazyScrambler = lazyScramblers.get(puzzle);
            final Puzzle scrambler = lazyScrambler.cachedInstance();
            
            start = new TimedLogRecordStart(Level.INFO, "Are " + THREE_BY_THREE_SCRAMBLE_COUNT + " " + puzzle + " one move away from solved?");
            l.log(start);
            for(int count = 0; count < SCRAMBLE_COUNT; count++){
                PuzzleState state = scrambler.getSolvedState().applyAlgorithm(scrambler.generateWcaScramble(r));
                state.solveIn(1);
            }
            l.log(start.finishedNow());
        }
    }
}
