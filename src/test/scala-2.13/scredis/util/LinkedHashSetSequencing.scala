package scredis.util

import org.scalactic.Equality
import org.scalatest.enablers.Sequencing
import scredis.util.LinkedHashSet

object LinkedHashSetSequencing {
  implicit def linkedHashSetSequencing[A](implicit equality: Equality[A]): Sequencing[LinkedHashSet[A]] = new Sequencing[LinkedHashSet[A]] {
    override def containsInOrder(sequence: LinkedHashSet[A], eles: collection.Seq[Any]): Boolean = {
      val it = eles.iterator
      for (e <- sequence) {
        it.takeWhile(_ != e)
        if (it.hasNext) it.next()
        else return false
      }
      true
    }

    override def containsInOrderOnly(sequence: LinkedHashSet[A], eles: collection.Seq[Any]): Boolean = {
      sequence.toList == eles.toList
    }

    override def containsTheSameElementsInOrderAs(leftSequence: LinkedHashSet[A], rightSequence: Iterable[Any]): Boolean =
      leftSequence.toList == rightSequence.toList
  }

}
