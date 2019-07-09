package scredis.util

import scala.collection.mutable
import scala.collection.mutable.{LinkedHashSet => MLinkedHashSet}

/**
 * Represents an '''immutable''' linked hash set.
 */
class LinkedHashSet[A](elems: A*) extends Set[A] with Serializable {

  override def className = "LinkedHashSet"

  private val set = MLinkedHashSet[A](elems: _*)

  override def incl(elem: A): LinkedHashSet[A] = {
    if (set.contains(elem)) {
      this
    } else {
      LinkedHashSet.newBuilder.addAll(set).addOne(elem).result()
    }
  }

  def reverse: LinkedHashSet[A] = {
    LinkedHashSet.newBuilder.addAll(set.toSeq.reverseIterator).result()
  }

  override def excl(elem: A): LinkedHashSet[A] = {
    if (set.contains(elem)) {
      LinkedHashSet.newBuilder.addAll(set.filter(_ != elem)).result()
    } else {
      this
    }
  }

  override def contains(elem: A): Boolean = set.contains(elem)

  override def iterator: Iterator[A] = set.iterator
}

object LinkedHashSet extends scala.collection.IterableFactory[LinkedHashSet] {
  
  class LinkedHashSetBuilder[A]() extends mutable.Builder[A, LinkedHashSet[A]] {
    protected var elems: MLinkedHashSet[A] = MLinkedHashSet.empty
    def addOne(x: A): this.type = { elems += x; this }
    def clear(): Unit = { elems = MLinkedHashSet.empty }
    def result: LinkedHashSet[A] = new LinkedHashSet[A](elems.toList: _*)
  }

  override def newBuilder[A]: mutable.Builder[A, LinkedHashSet[A]] =
    new LinkedHashSetBuilder[A]()

  override def empty[A]: LinkedHashSet[A] = new LinkedHashSet[A]

  override def from[A](source: IterableOnce[A]): LinkedHashSet[A] = new LinkedHashSet[A](source.iterator.to(List): _*)
}

