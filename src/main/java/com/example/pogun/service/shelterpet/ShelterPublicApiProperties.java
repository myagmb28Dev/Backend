package com.example.pogun.service.shelterpet;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 국가동물보호정보시스템 구조동물 조회 API 설정값이다.
 */
@ConfigurationProperties(prefix = "app.shelter.api")
public class ShelterPublicApiProperties {

    private String baseUrl = "https://apis.data.go.kr/1543061/abandonmentPublicService_v2";
    private String serviceKey;
    private String abandonmentPath = "/abandonmentPublic_v2";
    private String sidoPath = "/sido_v2";
    private String sigunguPath = "/sigungu_v2";
    private String shelterPath = "/shelter_v2";
    private String kindPath = "/kind_v2";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public String getAbandonmentPath() {
        return abandonmentPath;
    }

    public void setAbandonmentPath(String abandonmentPath) {
        this.abandonmentPath = abandonmentPath;
    }

    public String getSidoPath() {
        return sidoPath;
    }

    public void setSidoPath(String sidoPath) {
        this.sidoPath = sidoPath;
    }

    public String getSigunguPath() {
        return sigunguPath;
    }

    public void setSigunguPath(String sigunguPath) {
        this.sigunguPath = sigunguPath;
    }

    public String getShelterPath() {
        return shelterPath;
    }

    public void setShelterPath(String shelterPath) {
        this.shelterPath = shelterPath;
    }

    public String getKindPath() {
        return kindPath;
    }

    public void setKindPath(String kindPath) {
        this.kindPath = kindPath;
    }
}
