/*
    ti5x calculator emulator -- command line event loop

    Copyright 2026 Pascal Obry <pascal@obry.net>.

    This program is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
    A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.obry.ti5x.cli;

import java.util.PriorityQueue;

import net.obry.ti5x.engine.Scheduler;

final class ConsoleScheduler implements Scheduler {
  /* Queue of tasks run by the main thread (Take). Other threads (the
     interactive input) may post tasks. In fast mode the delays are
     skipped: time is virtual and jumps to the next task. */

  private static final class Entry {
    final long When;
    final long Seq; /* keeps posting order for tasks due at the same time */
    final Runnable Task;

    Entry(long When, long Seq, Runnable Task) {
      this.When = When;
      this.Seq = Seq;
      this.Task = Task;
    }
  }

  private final PriorityQueue<Entry> Queue = new PriorityQueue<>
     (
        (A, B) -> A.When != B.When ? Long.compare(A.When, B.When) : Long.compare(A.Seq, B.Seq)
     );
  private final boolean Fast;
  private long Seq = 0;
  private long VirtualNow = 0;
  private boolean InputOpen = false; /* another thread may still post tasks */

  ConsoleScheduler
     (
        boolean Fast
     ) {
    this.Fast = Fast;
  }

  private long Now() {
    return Fast ? VirtualNow : System.currentTimeMillis();
  }

  @Override
  public synchronized boolean post(Runnable Task) {
    return postDelayed(Task, 0);
  }

  @Override
  public synchronized boolean postDelayed(Runnable Task, long DelayMillis) {
    Queue.add(new Entry(Now() + Math.max(0, DelayMillis), Seq++, Task));
    notifyAll();
    return true;
  }

  @Override
  public synchronized void removeCallbacks(Runnable Task) {
    Queue.removeIf(E -> E.Task == Task);
  }

  synchronized void SetInputOpen
     (
        boolean Open
     ) {
    InputOpen = Open;
    notifyAll();
  }

  synchronized boolean IsEmpty() {
    return Queue.isEmpty();
  }

  synchronized Runnable Take
     (
        boolean WaitForInput /* when the queue is empty, wait for tasks from the input thread */
     )
     throws InterruptedException {
    /* returns the next task when it is due, null if there is none. */
    for (; ; ) {
      final Entry Next = Queue.peek();
      if (Next == null) {
        if (!WaitForInput || !InputOpen)
          return null;
        wait();
        continue;
      }
      final long Delay = Next.When - Now();
      if (Delay <= 0) {
        return Queue.poll().Task;
      } else if (Fast) {
        VirtualNow = Next.When;
      } else {
        wait(Delay); /* a task posted meanwhile may be due earlier */
      }
    }
  }
}
