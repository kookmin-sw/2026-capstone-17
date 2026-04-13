package com.capstone.focus.domain.base

import com.github.f4b6a3.ulid.UlidCreator
import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.domain.Persistable
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.time.LocalDateTime
import java.util.*

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class UlidPrimaryKeyEntity : Persistable<String> {
    @Id
    @Column(name = "id")
    private val id: String = UlidCreator.getMonotonicUlid().toString()

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    @Transient
    private var _isNew = true

    override fun isNew(): Boolean = _isNew

    override fun getId(): String = id

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is HibernateProxy && this::class != other::class) return false
        return id == getIdentifier(other)
    }

    private fun getIdentifier(obj: Any): Serializable {
        return if (obj is HibernateProxy) {
            obj.hibernateLazyInitializer.identifier as Serializable
        } else {
            (obj as UlidPrimaryKeyEntity).id
        }
    }

    override fun hashCode() = Objects.hashCode(id)

    @PostPersist
    @PostLoad
    protected fun load() {
        _isNew = false
    }
}