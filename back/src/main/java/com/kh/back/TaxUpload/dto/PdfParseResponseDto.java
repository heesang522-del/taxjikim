
package com.kh.back.TaxUpload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfParseResponseDto {

    private String fileName;

    private int totalPages;

    private int extractedCount;

    private List<TaxTransactionDto> transactions;

}
