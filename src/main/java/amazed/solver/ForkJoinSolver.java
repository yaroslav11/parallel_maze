package amazed.solver;

import amazed.maze.Maze;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
    extends SequentialSolver
{
    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */

    private volatile boolean completed = false;
    private static Set<Integer> lockalVisited= new ConcurrentSkipListSet<>();

    public ForkJoinSolver(Maze maze)
    {
        super(maze);
        super.visited = lockalVisited;
//        this.previousPath = new ArrayList<>();
    }

//    List<Integer> previousPath;

    public ForkJoinSolver(Maze maze,
                          Set<Integer> visited,
//                          Map<Integer, Integer> predecessor,
//                          List<Integer> previousPath,
                          int start) {
        super(maze);
        super.visited = lockalVisited;
//        super.visited = visited;
//        super.predecessor = predecessor;
//        this.previousPath = previousPath;
        super.start = start;
}

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter)
    {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     */
    @Override
    public List<Integer> compute()
    {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {
        {
            // one player active on the maze at start
            int player = maze.newPlayer(start);
            // start with start node
            frontier.push(start);
            // as long as not all nodes have been processed
            while (!frontier.empty() && !completed) {
                // get the new node to process
                int current = frontier.pop();
                // if current node has a goal
                if (maze.hasGoal(current)) {
                    // move player to goal
                    maze.move(player, current);
                    // search finished: reconstruct and return path
//                    System.out.println("OK" + String.valueOf(pathFromTo(start, current).size()));
                    completed=true;
                    return pathFromTo(start, current);
                }
                // if current node has not been visited yet
                if (!visited.contains(current)) {
                    // move player to current node
                    maze.move(player, current);
                    // mark node as visited
                    visited.add(current);
                    // for every node nb adjacent to current
                    Set<Integer> neighbours = maze.neighbors(current);
                    if (neighbours.size()<=2) {
                        for (int nb : neighbours) {
                            // add nb to the nodes to be processed
                            if (visited.contains(nb)) continue;
                            frontier.push(nb);
                            // if nb has not been already visited,
                            // nb can be reached from current (i.e., current is nb's predecessor)
                            if (!visited.contains(nb))
                                predecessor.put(nb, current);
                        }
                    }
                    else {
                        List<ForkJoinSolver> forkJoinSolvers = new ArrayList<>();
                        List<Integer> prevP = pathFromTo(start, current);
                        for (int nb : neighbours) {
                            if (visited.contains(nb)) continue;
                            ForkJoinSolver tmp = new ForkJoinSolver(maze, visited, nb);
                            forkJoinSolvers.add(tmp);
                            tmp.fork();
                        }

                        for (ForkJoinSolver task: forkJoinSolvers) {
                            List<Integer> integers = task.join();
                            if(integers != null) {
                                prevP.addAll(integers);
                                completed = true;
                                return prevP;
                            }
                        }
                        return null;
                    }
                }
            }
            // all nodes explored, no goal found
            return null;
        }
    }

}
