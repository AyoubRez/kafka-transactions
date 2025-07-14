package com.appsdeveloperblog.estore.WithdrawalService;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EmbeddedKafka
@SpringBootTest(properties = "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}")
public class ProductCreatedEventHandlerIntegrationTest {

    @MockBean
    ProccessedEventRepository proccessedEventRepository;

    @MockBean
    RestTemplate restTemplate;

    @Autowired
    KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;

    @SpyBean // used because we want to keep the original behavior of
    // product created Event handler and still be able to intercept
    // method calls and perform additional verifications ( ex :
    // when handle method is invoked i can capture method arguments
    // and verify their values
    ProductCreatedEventHandler productCreatedEventHandler;

    @Test
    public void testProductCreatedEventHandler_OnProductCreated_handlesEvent() throws Exception {

        //Arrange
        String title = "iphone 11";
        BigDecimal price = new BigDecimal("12.34");
        Integer quantity = 1;

        ProductCreatedEvent productCreatedEvent = new ProductCreatedEvent();
        productCreatedEvent.setPrice(price);
        productCreatedEvent.setProductId(UUID.randomUUID().toString());
        productCreatedEvent.setQuantity(quantity);
        productCreatedEvent.setTitle(title);

        String messageId = UUID.randomUUID().toString();
        String messageKey = productCreatedEvent.getProductId();

        ProducerRecord<String, ProductCreatedEvent> record = new ProducerRecord<>(
                "product-created-events-topic",
                messageKey,
                productCreatedEvent
        );
        record.headers().add("messageId", messageId.getBytes());
        record.headers().add(KafkaHeaders.RECEIVED_KEY, messageKey.getBytes());

        ProcessesEventEntity processesEventEntity = new ProcessesEventEntity();
        when(proccessedEventRepository.findByMessageId(anyString())).thenReturn(processesEventEntity);
        when(proccessedEventRepository.save(any(ProcessesEventEntity.class))).thenReturn(null);

        String responseBody = "{\"key\":\"value\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                isNull(),
                eq(String.class)
        )).thenReturn(responseEntity);

        //Act
        kafkaTemplate.send(record).get();

        //Assert
        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class); // capture argument values for further assertions
        // used it to check if our method was invoked with certain arguments
        ArgumentCaptor<String> messageKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ProductCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ProductCreatedEvent.class);

        verify(productCreatedEventHandler,timeout(5000).times(1))
                .handle(eventCaptor.capture(),
                        messageIdCaptor.capture(),
                        messageKeyCaptor.capture());
        /* verify : ensures a certain method is called on the spy in this case
        *  I m checking if the handle method was called with three parameters
        * the timeOut method specifies that the verify method should wait up to five seconds for handle
        * method to be invoked
        * the times method indicates that the handle method should be invoked exacly One time
        * and if any of these requirements are not satisfied then the exception will be thrown
        * the test will fail then
        * */

        assertEquals(messageId,messageIdCaptor.getValue());
        assertEquals(messageKey,messageKeyCaptor.getValue());
        assertEquals(productCreatedEvent.getProductId(),eventCaptor.getValue().getProductId());

    }
}
