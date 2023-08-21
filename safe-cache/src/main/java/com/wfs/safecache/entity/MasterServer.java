package com.wfs.safecache.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MasterServer {
    private String ip;
    private String port;
}
