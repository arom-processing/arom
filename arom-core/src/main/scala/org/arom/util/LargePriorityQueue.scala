/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2010, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// Adapted from the scala library for the ability to specify initial heap capacity

package org.arom.util

import scala.collection._
import mutable.{Queue, ResizableArray, Builder, Cloneable}
import scala.collection.generic._
//import scala.collection.mutable._
import annotation.migration

/** This class implements priority queues using a heap.
 *  To prioritize elements of type T there must be an implicit
 *  Ordering[T] available at creation.
 *  
 *  @tparam A    type of the elements in this priority queue.
 *  @param ord   implicit ordering used to compare the elements of type `A`.
 *  
 *  @author  Matthias Zenger
 *  @version 1.0, 03/05/2004
 *  @since   1
 *  
 *  @define Coll PriorityQueue
 *  @define coll priority queue
 *  @define orderDependent
 *  @define orderDependentFold
 *  @define mayNotTerminateInf
 *  @define willNotTerminateInf
 */
@serializable @cloneable
class LargePriorityQueue[A](initialCapacity: Int)(implicit ord: Ordering[A])
      extends Seq[A]
      with SeqLike[A, LargePriorityQueue[A]]
      with Growable[A]
      with Cloneable[LargePriorityQueue[A]]
      with Builder[A, LargePriorityQueue[A]]
{
  import ord._

  private final class ResizableArrayAccess[A] extends ResizableArray[A] {
    @inline def p_size0 = size0
    @inline def p_size0_=(s: Int) = size0 = s
    @inline def p_array = array
    @inline def p_ensureSize(n: Int) = super.ensureSize(n)
    @inline def p_swap(a: Int, b: Int) = super.swap(a, b)
    ensureSize(initialCapacity)
  }

  protected[this] override def newBuilder = new LargePriorityQueue[A](initialCapacity)

  private val resarr = new ResizableArrayAccess[A]

  resarr.p_size0 += 1                           // we do not use array(0)
  override def length: Int = resarr.length - 1  // adjust length accordingly
  override def size: Int = length
  override def isEmpty: Boolean = resarr.p_size0 < 2
  override def repr = this
  
  // hey foreach, our 0th element doesn't exist
  override def foreach[U](f: A => U) {
    var i = 1
    while (i < resarr.p_size0) {
      f(toA(resarr.p_array(i)))
      i += 1
    }
  }

  def update(idx: Int, elem: A) {
    if (idx < 0 || idx >= size) throw new IndexOutOfBoundsException("Indices must be nonnegative and lesser than the size.")

    var i = 0
    val iter = iterator
    clear
    while (iter.hasNext) {
      val curr = iter.next
      if (i == idx) this += elem
      else this += curr
      i += 1
    }
  }

  def apply(idx: Int) = {
    if (idx < 0 || idx >= size) throw new IndexOutOfBoundsException("Indices must be nonnegative and lesser than the size.")
    
    var left = idx
    val iter = iterator
    var curr = iter.next
    while (left > 0) {
      curr = iter.next
      left -= 1
    }
    curr
  }

  def result = clone

  private def toA(x: AnyRef): A = x.asInstanceOf[A]
  protected def fixUp(as: Array[AnyRef], m: Int): Unit = {
    var k: Int = m
    while (k > 1 && toA(as(k / 2)) < toA(as(k))) {
      resarr.p_swap(k, k / 2)
      k = k / 2
    }    
  }
  
  protected def fixDown(as: Array[AnyRef], m: Int, n: Int): Unit = {    
    var k: Int = m
    while (n >= 2 * k) {
      var j = 2 * k
      if (j < n && toA(as(j)) < toA(as(j + 1)))
        j += 1
      if (toA(as(k)) >= toA(as(j)))
        return
      else {
        val h = as(k)
        as(k) = as(j)
        as(j) = h
        k = j
      }
    }
  }
  
  @deprecated(
    "Use += instead if you intend to add by side effect to an existing collection.\n"+
    "Use `clone() +=' if you intend to create a new collection."
  )
  def +(elem: A): LargePriorityQueue[A] = { this.clone() += elem }

  /** Add two or more elements to this set. 
   *  @param    elem1 the first element.
   *  @param    kv2 the second element.
   *  @param    kvs the remaining elements.
   */
  @deprecated(
    "Use ++= instead if you intend to add by side effect to an existing collection.\n"+
    "Use `clone() ++=' if you intend to create a new collection."
  )
  def +(elem1: A, elem2: A, elems: A*) = { this.clone().+=(elem1, elem2, elems : _*) }

  /** Inserts a single element into the priority queue.
   *
   *  @param  elem        the element to insert.
   *  @return             this $coll.
   */
  def +=(elem: A): this.type = {
    resarr.p_ensureSize(resarr.p_size0 + 1)
    resarr.p_array(resarr.p_size0) = elem.asInstanceOf[AnyRef]
    fixUp(resarr.p_array, resarr.p_size0)
    resarr.p_size0 += 1
    this
  }

  /** Adds all elements provided by a `TraversableOnce` object
   *  into the priority queue.
   *
   *  @param  xs    a traversable object.
   *  @return       a new priority queue containing elements of both `xs` and `this`.
   */
  def ++(xs: TraversableOnce[A]) = { this.clone() ++= xs }

  /** Adds all elements to the queue.
   *
   *  @param  elems       the elements to add.
   */
  def enqueue(elems: A*): Unit = { this ++= elems }

  /** Returns the element with the highest priority in the queue,
   *  and removes this element from the queue.
   *
   *  @throws Predef.NoSuchElementException
   *  @return   the element with the highest priority.
   */
  def dequeue(): A =
    if (resarr.p_size0 > 1) {
      resarr.p_size0 = resarr.p_size0 - 1
      resarr.p_swap(1, resarr.p_size0)
      fixDown(resarr.p_array, 1, resarr.p_size0 - 1)
      toA(resarr.p_array(resarr.p_size0))
    } else
      throw new NoSuchElementException("no element to remove from heap")

  /** Returns the element with the highest priority in the queue,
   *  or throws an error if there is no element contained in the queue.
   *
   *  @return   the element with the highest priority.
   */
  def max: A = if (resarr.p_size0 > 1) toA(resarr.p_array(1)) else throw new NoSuchElementException("queue is empty")

  /** Removes all elements from the queue. After this operation is completed,
   *  the queue will be empty.
   */
  def clear(): Unit = { resarr.p_size0 = 1 }

  /** Returns an iterator which yields all the elements of the priority
   *  queue in descending priority order.
   *
   *  @return  an iterator over all elements sorted in descending order.
   */
  override def iterator: Iterator[A] = new Iterator[A] {
    val as: Array[AnyRef] = new Array[AnyRef](resarr.p_size0)
    Array.copy(resarr.p_array, 0, as, 0, resarr.p_size0)
    var i = resarr.p_size0 - 1
    def hasNext: Boolean = i > 0
    def next(): A = {
      val res = toA(as(1))
      as(1) = as(i)
      i = i - 1
      fixDown(as, 1, i)
      res
    }
  }
  
  
  /** Returns the reverse of this queue. The priority queue that gets
   *  returned will have an inversed ordering - if for some elements
   *  `x` and `y` the original queue's ordering
   *  had `compare` returning an integer ''w'', the new one will return ''-w'',
   *  assuming the original ordering abides its contract.
   *  
   *  Note that the order of the elements will be reversed unless the
   *  `compare` method returns 0. In this case, such elements
   *  will be subsequent, but their corresponding subinterval may be inappropriately
   *  reversed. However, due to the compare-equals contract, they will also be equal.
   *  
   *  @return   A reversed priority queue.
   */
  override def reverse = {
    val revq = new LargePriorityQueue[A](initialCapacity)(new math.Ordering[A] {
      def compare(x: A, y: A) = ord.compare(y, x)
    })
    for (i <- 1 until resarr.length) revq += resarr(i)
    revq
  }
  
  override def reverseIterator = new Iterator[A] {
    val arr = new Array[Any](LargePriorityQueue.this.size)
    iterator.copyToArray(arr)
    var i = arr.size - 1
    def hasNext: Boolean = i >= 0
    def next(): A = {
      val curr = arr(i)
      i -= 1
      curr.asInstanceOf[A]
    }
  }
  
  /** The hashCode method always yields an error, since it is not
   *  safe to use mutable queues as keys in hash tables.
   *
   *  @return never.
   */
  override def hashCode(): Int =
    throw new UnsupportedOperationException("unsuitable as hash key")

  /** Returns a regular queue containing the same elements.
   */
  def toQueue: Queue[A] = new Queue[A] ++= this.iterator

  /** Returns a textual representation of a queue as a string.
   *
   *  @return the string representation of this queue.
   */
  override def toString() = toList.mkString("PriorityQueue(", ", ", ")")
  override def toList = this.iterator.toList

  /** This method clones the priority queue.
   *
   *  @return  a priority queue with the same elements.
   */
  override def clone(): LargePriorityQueue[A] = new LargePriorityQueue[A](initialCapacity) ++= this.iterator

  // def printstate {
  //   println("-----------------------")
  //   println("Size: " + resarr.p_size0)
  //   println("Internal array: " + resarr.p_array.toList)
  //   println(toString)
  // }
}

// !!! TODO - but no SortedSeqFactory (yet?)
// object PriorityQueue extends SeqFactory[PriorityQueue] {  
//   def empty[A](implicit ord: Ordering[A]): PriorityQueue[A] = new PriorityQueue[A](ord)
//   implicit def canBuildFrom[A](implicit ord: Ordering[A]): CanBuildFrom[Coll, A, PriorityQueue] = 
// }
// 
