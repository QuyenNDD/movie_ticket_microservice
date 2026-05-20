package com.movie.booking_service.dto;

import lombok.Data;

@Data
public class SeatMatrixItemDTO {
    private String id;
    private String row;     // FE muốn tên là 'row' thay vì 'rowName'
    private String col;     // FE muốn tên là 'col' thay vì 'seatLabel'
    private String type;
    private String status;

    // 💡 LỜI KHUYÊN XƯƠNG MÁU: Vẫn nên nhét lén 2 trường này vào cho FE
    private int gridRow;
    private int gridColumn;
}
