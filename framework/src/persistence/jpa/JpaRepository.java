/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package persistence.jpa;



import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;
import javax.persistence.Query;

/**
 * An utility abstract class for implementing JPA repositories.
 *
 * @author Paulo Gandra Sousa
 *
 * <p>
 * based on <a
 * href="http://stackoverflow.com/questions/3888575/single-dao-generic-crud-methods-jpa-hibernate-spring">stackoverflow</a>
 * and on <a href="https://burtbeckwith.com/blog/?p=40">burtbeckwith</a>.
 * <p>
 * also have a look at <a
 * href="http://blog.xebia.com/tag/jpa-implementation-patterns/">JPA
 * implementation patterns</a>
 *
 * @param <T> the entity type that we want to build a repository for
 * @param <ID> the key type of the entity
 */
public abstract class JpaRepository<T, ID extends Serializable> {

    @PersistenceUnit
    private static EntityManagerFactory emFactory;

    protected EntityManagerFactory entityManagerFactory() {
        if (emFactory == null) {
            emFactory = Persistence
                    .createEntityManagerFactory(persistenceUnitName());
        }
        return emFactory;
    }

    private final Class<T> entityClass;
    private EntityManager _entityManager;

    @SuppressWarnings("unchecked")
    public JpaRepository() {
        ParameterizedType genericSuperclass = (ParameterizedType) getClass()
                .getGenericSuperclass();
        this.entityClass = (Class<T>) genericSuperclass
                .getActualTypeArguments()[0];
    }

    protected EntityManager entityManager() {
        if (_entityManager == null || !_entityManager.isOpen()) {
            _entityManager = entityManagerFactory().createEntityManager();
        }
        return _entityManager;
    }

    /**
     * adds a new entity to the eapli.myapp.persistence store
     *
     * @param entity
     * @return the newly created persistent object
     */
    public T create(T entity) {
        this.entityManager().persist(entity);
        return entity;
    }

    /**
     * reads an entity given its ID
     *
     * @param id
     * @return
     */
    public T read(ID id) {
        return this.entityManager().find(entityClass, id);
    }

    /**
     * reads an entity given its ID
     *
     * @param id
     * @return
     */
    public T findById(ID id) {
        return read(id);
    }

    public T update(T entity) {
        return entityManager().merge(entity);
    }

    /**
     * removes the object from the eapli.myapp.persistence storage. the object reference is
     * still valid but the persisted entity is/will be deleted
     *
     * @param entity
     */
    public void delete(T entity) {
        entity = entityManager().merge(entity);
        entityManager().remove(entity);
    }

    /**
     * returns the number of entities in the eapli.myapp.persistence store
     *
     * @return the number of entities in the eapli.myapp.persistence store
     */
    public long size() {
        return (Long) entityManager().createQuery(
                "SELECT COUNT(*) FROM " + entityClass.getSimpleName())
                .getSingleResult();
    }

    /**
     * checks for the existence of an entity with the provided ID.
     *
     * @param key
     * @return
     */
    boolean containsEntity(ID key) {
        return findById(key) != null;
    }

    // TODO since repositories should mimic lists this method should not exist
    // and the class should implement iterator()
    @SuppressWarnings("unchecked")
    public Collection<T> findAll() {
        return entityManager().createQuery(
                "SELECT e FROM " + entityClass.getSimpleName() + " e")
                .getResultList();
    }

    /**
     * adds <b>and commits</b> a new entity to the eapli.myapp.persistence store
     *
     *
     * TODO it is controversial if the repository class should have explicit
     * knowledge of when to start a transaction and end it as well as to know
     * when to open a connection and close it. this is the kind of stuff that
     * the container (e.g., web server) should handle declaratively
     *
     * the following methods open and commit a transaction: add() save()
     * replace() remove()
     *
     * note that other methods in this class just work with the JPA unit of work
     * and expect the container to begin/commit transactions. they are: create()
     * update() delete()
     *
     * @param entity
     * @return the newly created persistent object
     */
    public boolean add(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException();
        }

        EntityManager em = entityManager();
        try {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            em.persist(entity);
            tx.commit();
        } finally {
            em.close();
        }
        return true;
    }


    /**
     * inserts or updates an entity <b>and commits</b>.
     *
     * note that you should reference the return value to use the persisted
     * entity, as the original object passed as argument might be copied to a
     * new object
     *
     * check
     * <a
     * href="http://blog.xebia.com/2009/03/23/jpa-implementation-patterns-saving-detached-entities/">JPA
     * implementation patterns</a>
     * for a discussion on saveOrUpdate() behavior and merge()
     *
     * @param entity
     * @return the persisted entity - might be a different object than the
     * parameter
     */
    public T save(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException();
        }

        // the following code atempts to do a save or update by checking for
        // eapli.myapp.persistence exceptions while doing persist()
        // this could be made more efficient if we check if the entity has an
        // autogenerated id
        EntityManager em = entityManager();
        assert em != null;
        try {
            // transaction will be rolled back if any exception occurs
            // we are especially interested in "detached entity" meaning that the object already exists
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                em.persist(entity);
                tx.commit();
            } catch (PersistenceException ex) {
                // we need to set up a new transaction if persist() raises an
                // exception
                tx = em.getTransaction();
                tx.begin();
                entity = em.merge(entity);
                tx.commit();
            }
        } finally {
            // we are closing the entity manager here because this code is runing in
            // a non-container managed way. if it was the case to be runing under an
            // application server with a JPA container and managed transactions/sessions,
            // one should not be doing this
            em.close();
        }

        return entity;
    }

    /**
     * returns the first n entities according to its "natural" order
     *
     * @param n
     * @return
     */
    public List<T> first(int n) {
        Query q = entityManager().createQuery(
                "SELECT e FROM " + entityClass.getSimpleName() + " e");
        q.setMaxResults(n);

        return q.getResultList();
    }

    public T first() {
        List<T> r = first(1);
        return (r.isEmpty() ? null : r.get(0));
    }

    public T last() {
        throw new UnsupportedOperationException();
    }

    public List<T> page(int pageNumber, int pageSize) {
        Query q = entityManager().createQuery(
                "SELECT e FROM " + entityClass.getSimpleName() + " e");
        q.setMaxResults(pageSize);
        q.setFirstResult((pageNumber - 1) * pageSize);

        return q.getResultList();
    }

    private class JpaPagedIterator<T> implements Iterator<T> {

        private final JpaRepository<T, ID> repository;
        private final int pageSize;
        private int currentPageNumber;
        private Iterator<T> currentPage;

        private JpaPagedIterator(JpaRepository<T, ID> repository, int pagesize) {
            this.repository = repository;
            this.pageSize = pagesize;
        }

        @Override
        public boolean hasNext() {
            if (needsToLoadPage()) {
                loadNextPage();
            }
            return currentPage.hasNext();
        }

        @Override
        public T next() {
            if (needsToLoadPage()) {
                loadNextPage();
            }
            return currentPage.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        private void loadNextPage() {
            List<T> page = repository.page(++currentPageNumber, pageSize);
            currentPage = page.iterator();
        }

        private boolean needsToLoadPage() {
            // either we do not have an iterator yet or we have reached the end of the (current) iterator
            return (currentPage == null || !currentPage.hasNext());
        }
    }

    /**
     * returns a paged iterator
     *
     * @return
     */
    public Iterator<T> iterator(int pagesize) {
        return new JpaPagedIterator<T>(this, pagesize);
    }

    public List<T> all() {
        // TODO check performance impact of this 'where' clause
        return match("1=1");

        // EntityManager em = entityManager();
        // assert em != null;
        //
        // String tableName = entityClass.getName();
        // //entityClass.getAnnotation(Table.class).name();
        // Query q = em.createQuery("SELECT it FROM " + tableName + " it");
        // List<T> all = q.getResultList();
        // return all;
    }

    /**
     * helper method. not to be exposed as public in any situation.
     *
     * @param where
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<T> match(String where) {
        String className = entityClass.getSimpleName(); // entityClass.getAnnotation(Table.class).name();
        Query q = entityManager().
                createQuery("SELECT it FROM " + className + " it WHERE "
                        + where);
        List<T> some = q.getResultList();
        return some;
    }

    /**
     * derived classes should implement this method to return the name of the
     * eapli.myapp.persistence unit
     *
     * @return the name of the eapli.myapp.persistence unit
     */
    protected abstract String persistenceUnitName();
}
