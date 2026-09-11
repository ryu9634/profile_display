package com.portfolio.model;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사이드바 메뉴 한 칸에 해당하는 카테고리입니다.
 *
 * id 를 직접 정하는 이유는 주소(#/artwork)에 그대로 노출되기 때문입니다.
 * main 과 cv 는 사이트 구조상 없으면 화면이 깨지므로 isDeletable=false 로 잠급니다.
 */
@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(name = "is_deletable", nullable = false)
    private Boolean isDeletable = true;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "created_at")
    private Long createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = System.currentTimeMillis();
        if (isDeletable == null) {
            isDeletable = true;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}
