package com.capstone.focus.domain.repository

import com.capstone.focus.domain.entity.BroadcastMediaAsset
import org.springframework.data.jpa.repository.JpaRepository

interface BroadcastMediaAssetRepository : JpaRepository<BroadcastMediaAsset, String>
