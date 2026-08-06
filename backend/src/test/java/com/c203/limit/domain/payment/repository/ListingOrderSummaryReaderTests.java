package com.c203.limit.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.c203.limit.domain.payment.repository.ListingOrderSummaryReader.ListingOrderSummary;

@ExtendWith(MockitoExtension.class)
class ListingOrderSummaryReaderTests {

    @Mock JdbcClient jdbcClient;
    @Mock JdbcClient.StatementSpec statementSpec;
    @Mock JdbcClient.MappedQuerySpec<ListingOrderSummary> mappedQuerySpec;

    @Test
    void skipsQueryEntirelyWhenNoListingIdIsGiven() {
        ListingOrderSummaryReader reader = new ListingOrderSummaryReader(jdbcClient);

        assertThat(reader.findByIds(List.of())).isEmpty();

        verifyNoInteractions(jdbcClient);
    }

    @Test
    void bindsListingIdsAndReturnsMappedSummaries() {
        ListingOrderSummary summary =
                new ListingOrderSummary(1L, "제목", "ON_SALE", "https://cdn/1.jpg", "key/1.jpg");
        ListingOrderSummaryReader reader = new ListingOrderSummaryReader(jdbcClient);
        when(jdbcClient.sql(any(String.class))).thenReturn(statementSpec);
        when(statementSpec.param(eq("listingIds"), any())).thenReturn(statementSpec);
        when(statementSpec.query(
                        org.mockito.ArgumentMatchers.<RowMapper<ListingOrderSummary>>any()))
                .thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.list()).thenReturn(List.of(summary));

        assertThat(reader.findByIds(List.of(1L, 2L))).containsExactly(summary);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("FROM listing l")
                .contains("li.image_type = 'THUMBNAIL'")
                .contains("WHERE l.id IN (:listingIds)");
        verify(statementSpec).param("listingIds", List.of(1L, 2L));
    }

    @Test
    void mapsEveryProjectedColumnIncludingNullableImageColumns() throws Exception {
        ListingOrderSummaryReader reader = new ListingOrderSummaryReader(jdbcClient);
        when(jdbcClient.sql(any(String.class))).thenReturn(statementSpec);
        when(statementSpec.param(eq("listingIds"), any())).thenReturn(statementSpec);
        when(statementSpec.query(
                        org.mockito.ArgumentMatchers.<RowMapper<ListingOrderSummary>>any()))
                .thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.list()).thenReturn(List.of());

        reader.findByIds(List.of(9L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<ListingOrderSummary>> mapperCaptor =
                ArgumentCaptor.forClass(RowMapper.class);
        verify(statementSpec).query(mapperCaptor.capture());

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("listing_id")).thenReturn(9L);
        when(resultSet.getString("title")).thenReturn("갤럭시북4");
        when(resultSet.getString("status")).thenReturn("RESERVED");
        when(resultSet.getString("cdn_url")).thenReturn(null);
        when(resultSet.getString("s3_key")).thenReturn(null);

        ListingOrderSummary mapped = mapperCaptor.getValue().mapRow(resultSet, 0);

        assertThat(mapped.listingId()).isEqualTo(9L);
        assertThat(mapped.title()).isEqualTo("갤럭시북4");
        assertThat(mapped.status()).isEqualTo("RESERVED");
        assertThat(mapped.cdnUrl()).isNull();
        assertThat(mapped.s3Key()).isNull();
    }
}
