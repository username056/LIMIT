package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.Listing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {}
