package scredis.util

import scredis.util.LinkedHashSet.LinkedHashSetBuilder

import scala.collection.compat._
import scala.collection.mutable
import scala.collection.mutable.{LinkedHashSet => MLinkedHashSet}

/**
 * Represents an '''immutable''' linked hash set.
 */
class LinkedHashSet[A](elems: A*) extends Set[A] with Serializable {

  def className = "LinkedHashSet"

  private val set = MLinkedHashSet[A](elems: _*)

  override def empty: LinkedHashSet[A] = new LinkedHashSet[A]()

  def +(elem: A): LinkedHashSet[A] = incl(elem)

  def -(elem: A): LinkedHashSet[A] = excl(elem)

  def incl(elem: A): LinkedHashSet[A] = {
    if (set.contains(elem)) this
    else {
      set += elem
      new LinkedHashSet(set.toSeq: _*)
    }
  }

  def excl(elem: A): LinkedHashSet[A] = {
    if (set.contains(elem)) {
      set.remove(elem)
      new LinkedHashSet(set.toSeq: _*)
    } else this
  }

  def contains(elem: A): Boolean = set.contains(elem)

  def rangeImpl(from: Option[A], until: Option[A]): LinkedHashSet[A] = {
    (from, until) match {
      case (None, None) => this
      case (Some(from), None) => {
        val builder = LinkedHashSet.newBuilder[A]
        var fromFound = false
        this.foreach { elem =>
          if (fromFound) {
            builder += elem
          } else if (elem == from) {
            fromFound = true
            builder += elem
          }
        }
        builder.result()
      }
      case (None, Some(until)) => {
        val builder = LinkedHashSet.newBuilder[A]
        var untilFound = false
        this.foreach { elem =>
          if (elem == until) {
            return builder.result()
          } else {
            builder += elem
          }
        }
        builder.result()
      }
      case (Some(from), Some(until)) => {
        val builder = LinkedHashSet.newBuilder[A]
        var fromFound = false
        this.foreach { elem =>
          if (elem == until) {
            return builder.result()
          }

          if (fromFound) {
            builder += elem
          } else if (elem == from) {
            fromFound = true
            builder += elem
          }
        }
        builder.result()
      }
    }
  }

  def iterator: Iterator[A] = set.iterator

  def reverse: LinkedHashSet[A] = {
    val reversed = this.toList.reverse
    val builder = LinkedHashSet.newBuilder[A]
    reversed.foreach(e => builder += e)
    builder.result()
  }

}

object LinkedHashSet {

  class LinkedHashSetBuilder[A]() extends mutable.Builder[A, LinkedHashSet[A]] {
    protected var elems: MLinkedHashSet[A] = MLinkedHashSet.empty
    def addOne(x: A): this.type = { elems += x; this }
    def clear(): Unit = { elems = MLinkedHashSet.empty }
    def result: LinkedHashSet[A] = new LinkedHashSet[A](elems.toList: _*)

    def +=(elem: A): this.type = addOne(elem)      //(FOR 2.12)
  }

  implicit def linkedHashSetFactory[A]: Factory[A, LinkedHashSet[A]] = new Factory[A, LinkedHashSet[A]] {
    def apply(from: Nothing): mutable.Builder[A, LinkedHashSet[A]] = throw new UnsupportedOperationException()

    def apply(): mutable.Builder[A, LinkedHashSet[A]] = new LinkedHashSetBuilder[A]

    def fromSpecific(it: IterableOnce[A]): LinkedHashSet[A] = new LinkedHashSetBuilder[A].++=(it).result

    def newBuilder: mutable.Builder[A, LinkedHashSet[A]] = new LinkedHashSetBuilder[A]
  }

  def newBuilder[A]: mutable.Builder[A, LinkedHashSet[A]] = new LinkedHashSetBuilder[A]
}

