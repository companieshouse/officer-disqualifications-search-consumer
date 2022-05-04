package uk.gov.companieshouse.disqualifiedofficers.search.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.server.ResponseStatusException;

import uk.gov.companieshouse.api.disqualification.DisqualificationLinks;
import uk.gov.companieshouse.api.disqualification.OfficerDisqualification;
import uk.gov.companieshouse.disqualifiedofficers.search.handler.ApiResponseHandler;
import uk.gov.companieshouse.disqualifiedofficers.search.service.api.ApiClientService;
import uk.gov.companieshouse.disqualifiedofficers.search.transformer.ElasticSearchTransformer;
import uk.gov.companieshouse.logging.Logger;
import uk.gov.companieshouse.stream.EventRecord;
import uk.gov.companieshouse.stream.ResourceChangedData;

import java.io.IOException;
import java.io.InputStreamReader;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ResourceChangedProcessorTest {

    private ResourceChangedProcessor resourceChangedProcessor;
    private ApiResponseHandler apiResponseHandler;
    @Mock
    private ElasticSearchTransformer transformer;
    @Mock
    private Logger logger;
    @Mock
    private ApiClientService apiClientService;
    @Mock
    ResponseStatusException ex;


    @BeforeEach
    void setUp() {
        apiResponseHandler = new ApiResponseHandler();
        resourceChangedProcessor = new ResourceChangedProcessor(
                transformer,
                logger,
                apiClientService,
                apiResponseHandler
        );
    }

    @Test
    @DisplayName("Transforms a kafka message containing a payload into a search api object")
    void When_ValidMessage_Expect_ValidDisqualificationES6Mapping() throws IOException {
        Message<ResourceChangedData> mockChsResourceChangedData = createChsMessage();
        OfficerDisqualification officerDisqualification = new OfficerDisqualification();
        DisqualificationLinks links = new DisqualificationLinks();
        links.setSelf("disqualified-officers/natural/123456789");
        officerDisqualification.setLinks(links);
        when(transformer.getOfficerDisqualificationFromResourceChanged(
                mockChsResourceChangedData.getPayload())).thenReturn(officerDisqualification);

        resourceChangedProcessor.processResourceChanged(mockChsResourceChangedData);

        verify(transformer).getOfficerDisqualificationFromResourceChanged(mockChsResourceChangedData.getPayload());
        verify(apiClientService).putDisqualificationSearch("context_id", "123456789", officerDisqualification);
    }

    private Message<ResourceChangedData> createChsMessage() throws IOException {
        InputStreamReader exampleJsonPayload = new InputStreamReader(
                ClassLoader.getSystemClassLoader().getResourceAsStream("disqualified-officers-example.json"));
        String data = FileCopyUtils.copyToString(exampleJsonPayload);

        EventRecord eventRecord = new EventRecord();
        eventRecord.setPublishedAt("2022010351");
        eventRecord.setType("changed");
        
        String officerId = "1234567890";

        ResourceChangedData mockChsResourceChangedData = ResourceChangedData.newBuilder()
                .setData(data)
                .setContextId("context_id")
                .setResourceId(officerId)
                .setResourceKind("disqualified-officers")
                .setResourceUri(String.format("/disqualified-officers/natural/%s", officerId))
                .setEvent(eventRecord)
                .build();

        return MessageBuilder
                .withPayload(mockChsResourceChangedData)
                .setHeader(KafkaHeaders.RECEIVED_TOPIC, "test")
                .setHeader("CHANGED_RESOURCE_RETRY_COUNT", 1)
                .build();
    }
}
