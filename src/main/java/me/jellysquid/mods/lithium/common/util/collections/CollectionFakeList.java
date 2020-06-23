package me.jellysquid.mods.lithium.common.util.collections;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * @author 2No2Name
 * Class to be able to pass a Collection instead of a List to Mojang code where a list is not really necessary.
 * Many methods just throw UnsupportedOperationException, because the author is lazy.
 */
public class CollectionFakeList<T> implements List<T> {

    private final Collection<T> collection;
    private Iterator<T> iterator;
    private int iteratorIndex;

    public CollectionFakeList(Collection<T> c) {
        this.collection = c;
        this.iterator = this.collection.iterator();
    }

    @Override
    public int size() {
        return collection.size();
    }

    @Override
    public boolean isEmpty() {
        return collection.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return collection.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return collection.iterator();
    }

    @Override
    public void forEach(Consumer<? super T> consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        return collection.toArray();
    }

    @Override
    public boolean add(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(Predicate predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int i, Collection collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(UnaryOperator unaryOperator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sort(Comparator comparator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(int i) {
        if (i < 0) {
            throw new IndexOutOfBoundsException(""+ i);
        }
        if (i < this.iteratorIndex) {
            this.iterator = this.collection.iterator();
            this.iteratorIndex = 0;
        }
        while(i > this.iteratorIndex) {
            if (this.iterator.hasNext()) {
                this.iteratorIndex++;
                this.iterator.next();
            } else {
                throw new IndexOutOfBoundsException(""+ i);
            }
        }
        if (this.iterator.hasNext())
        {
            this.iteratorIndex++;
            return this.iterator.next();
        }
        throw new IndexOutOfBoundsException(""+ i);
    }

    @Override
    public T set(int i, Object o) {
        throw new UnsupportedOperationException();    }

    @Override
    public void add(int i, Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(int i) {
        throw new UnsupportedOperationException();    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException();    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> subList(int i, int i1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Spliterator<T> spliterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<T> stream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<T> parallelStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection collection) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object[] toArray(Object[] objects) {
        throw new UnsupportedOperationException();
    }
}
