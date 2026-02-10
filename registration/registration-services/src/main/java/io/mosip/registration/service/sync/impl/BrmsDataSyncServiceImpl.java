package io.mosip.registration.service.sync.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.service.BaseService;
import io.mosip.registration.service.sync.BrmsDataSyncService;

@Service
public class BrmsDataSyncServiceImpl extends BaseService implements BrmsDataSyncService {

    private static final Logger LOGGER =
            AppConfig.getLogger(BrmsDataSyncServiceImpl.class);

    private static final String BRMS_API_URL =
            "http://172.16.47.72:8081/api/stagingData/getDataFromStagingData";

//    private static final String BRMS_API_URL =
//            "http://172.16.47.72:8081/api/stagingPreReg/getdatafromprereg";

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ResponseDTO getCitizenDataByNrc(String nrcNumber) {

        LOGGER.info("Fetching citizen data from BRMS for NRC: {}", nrcNumber);

        ResponseDTO responseDTO = new ResponseDTO();

        try {
            /* ------------------------------
             * 1. Build request payload
             * ------------------------------ */
            Map<String, String> requestPayload = new HashMap<>();
            requestPayload.put("nrcm", nrcNumber);

            /* ------------------------------
             * 2. Build headers
             * ------------------------------ */
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(MediaType.parseMediaTypes("application/json"));

            HttpEntity<Map<String, String>> requestEntity =
                    new HttpEntity<>(requestPayload, headers);

            /* ------------------------------
             * 3. Execute POST call
             * ------------------------------ */
            ResponseEntity<Map> responseEntity =
                    restTemplate.exchange(
                            BRMS_API_URL,
                            HttpMethod.POST,
                            requestEntity,
                            Map.class
                    );

            LOGGER.info("BRMS HTTP Status: {}", responseEntity.getStatusCode());

            if (responseEntity.getStatusCode() != HttpStatus.OK
                    || responseEntity.getBody() == null
                    || responseEntity.getBody().isEmpty()) {

                LOGGER.error("Empty or invalid response from BRMS");
                setErrorResponse(responseDTO, "BRMS_INVALID_RESPONSE", null);
                return responseDTO;
            }

            /* ------------------------------
             * 4. Success response
             * ------------------------------ */
            Map<String, Object> citizenData = responseEntity.getBody();

            LOGGER.info("BRMS data fetched successfully for NRC: {}", nrcNumber);

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("citizenData", citizenData);

            setSuccessResponse(
                    responseDTO,
                    "BRMS data fetched successfully",
                    attributes
            );

        } catch (Exception e) {
            LOGGER.error("Error calling BRMS API for NRC: {}", nrcNumber, e);
            setErrorResponse(responseDTO, "BRMS_API_ERROR", null);
        }

        return responseDTO;
    }
}
