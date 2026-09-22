package com.baakio.repository;

import com.baakio.model.CommitmentRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommitmentRevisionRepository extends JpaRepository<CommitmentRevision, Long> {
    List<CommitmentRevision> findByCommitmentIdOrderById(long commitmentId);
}
