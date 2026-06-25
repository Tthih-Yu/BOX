package com.example.materialpull.repository;

import com.example.materialpull.entity.ExceptionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ExceptionEventRepository extends JpaRepository<ExceptionEventEntity, Long> {

}
