package com.bipros.udf.domain.repository;

import com.bipros.udf.domain.model.UdfDataType;
import com.bipros.udf.domain.model.UdfScope;
import com.bipros.udf.domain.model.UdfSubject;
import com.bipros.udf.domain.model.UserDefinedField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserDefinedFieldRepository extends JpaRepository<UserDefinedField, UUID> {
    List<UserDefinedField> findBySubject(UdfSubject subject);

    List<UserDefinedField> findBySubjectAndScope(UdfSubject subject, UdfScope scope);

    List<UserDefinedField> findBySubjectAndProjectId(UdfSubject subject, UUID projectId);

    long countByDataTypeAndSubject(UdfDataType dataType, UdfSubject subject);
}
