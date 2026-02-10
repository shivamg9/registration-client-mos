package io.mosip.registration.service.sync;

import io.mosip.registration.dto.ResponseDTO;


/**
 * Service interface for BRMS (Burmese Registration Management System) data synchronization.
 * This service handles fetching citizen data from BRMS database using NRC number.
 *
 * @since 1.0.0
 */
public interface BrmsDataSyncService {


    /**
     * Fetch citizen data from BRMS database using NRC number.
     *
     * @param nrcNumber the NRC number to search for
     * @return ResponseDTO containing the citizen data or error information
     */
    public ResponseDTO getCitizenDataByNrc(String nrcNumber);
}
