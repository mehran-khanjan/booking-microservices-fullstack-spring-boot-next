package com.example.commonlib.responseenvelope.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
  private List<T> content;
  private PageInfo pageInfo;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PageInfo {
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
  }
}
