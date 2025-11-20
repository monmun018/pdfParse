package com.example.demo.application.service;

import com.example.demo.domain.model.StatementMetadata;
import com.example.demo.domain.model.SuicaStatementRow;
import com.example.demo.infrastructure.pdf.PdfBoxMetadataReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PdfTextServiceTableParserTests {

    private static final float[] COLUMN_POSITIONS = new float[]{10f, 40f, 90f, 150f, 210f, 270f, 330f, 400f};

    private PdfTextService service;

    @BeforeEach
    void setUp() {
        service = new PdfTextService(mock(PdfBoxMetadataReader.class));
    }

    @Test
    void parseTableLinesAlignsColumnsBasedOnPositions() {
        List<PdfTextService.TableLine> lines = new ArrayList<>();
        lines.add(line(0f, "月", "日", "種別(入)", "利用駅(入)", "種別(出)", "利用駅(出)", "残高", "入金・利用金額"));
        lines.add(line(10f, "10", "", "", "", "", "", "¥21", "¥1,359"));
        lines.add(line(20f, "10", "21", "入", "小", "出", "登戸", "¥1,098", "-261"));
        lines.add(line(30f, "10", "21", "ｶｰﾄﾞ", "モバイル", "", "", "¥3,098", "+2,000"));

        StatementMetadata metadata = new StatementMetadata(null, null, null, null, LocalDate.of(2024, 10, 31));
        List<SuicaStatementRow> rows = service.parseTableLines(lines, metadata);

        assertThat(rows).hasSize(3);

        SuicaStatementRow carry = rows.get(0);
        assertThat(carry.yearMonth()).isEqualTo("2024-10");
        assertThat(carry.month()).isEqualTo("10");
        assertThat(carry.day()).isEmpty();
        assertThat(carry.typeIn()).isEmpty();
        assertThat(carry.stationIn()).isEmpty();
        assertThat(carry.typeOut()).isEmpty();
        assertThat(carry.stationOut()).isEmpty();
        assertThat(carry.balance()).isEqualTo("¥21");
        assertThat(carry.amount()).isEqualTo("¥1,359");

        SuicaStatementRow ride = rows.get(1);
        assertThat(ride.month()).isEqualTo("10");
        assertThat(ride.day()).isEqualTo("21");
        assertThat(ride.typeIn()).isEqualTo("入");
        assertThat(ride.stationIn()).isEqualTo("小");
        assertThat(ride.typeOut()).isEqualTo("出");
        assertThat(ride.stationOut()).isEqualTo("登戸");
        assertThat(ride.balance()).isEqualTo("¥1,098");
        assertThat(ride.amount()).isEqualTo("-261");

        SuicaStatementRow charge = rows.get(2);
        assertThat(charge.typeIn()).isEqualTo("ｶｰﾄﾞ");
        assertThat(charge.stationIn()).isEqualTo("モバイル");
        assertThat(charge.typeOut()).isEmpty();
        assertThat(charge.stationOut()).isEmpty();
        assertThat(charge.balance()).isEqualTo("¥3,098");
        assertThat(charge.amount()).isEqualTo("+2,000");
    }

    @Test
    void entryOnlyTypesClearExitColumnsEvenWhenTokensExist() {
        List<PdfTextService.TableLine> lines = new ArrayList<>();
        lines.add(line(0f, "月", "日", "種別(入)", "利用駅(入)", "種別(出)", "利用駅(出)", "残高", "入金・利用金額"));
        lines.add(line(10f, "10", "22", "物販", "", "出", "渋谷", "¥568", "-2,530"));

        StatementMetadata metadata = new StatementMetadata(null, null, null, null, LocalDate.of(2024, 10, 31));
        List<SuicaStatementRow> rows = service.parseTableLines(lines, metadata);

        assertThat(rows).hasSize(1);
        SuicaStatementRow retail = rows.get(0);
        assertThat(retail.typeIn()).isEqualTo("物販");
        assertThat(retail.typeOut()).isEmpty();
        assertThat(retail.stationOut()).isEmpty();
        assertThat(retail.balance()).isEqualTo("¥568");
        assertThat(retail.amount()).isEqualTo("-2,530");
    }

    private PdfTextService.TableLine line(float y, String... values) {
        PdfTextService.TableLine line = new PdfTextService.TableLine(y);
        for (int i = 0; i < values.length && i < COLUMN_POSITIONS.length; i++) {
            String value = values[i];
            if (value == null || value.isEmpty()) {
                continue;
            }
            float x = COLUMN_POSITIONS[i];
            line.addToken(new PdfTextService.PositionedToken(x, x + 15f, value));
        }
        return line;
    }
}
