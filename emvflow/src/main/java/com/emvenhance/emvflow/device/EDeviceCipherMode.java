/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2022/04/01                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.emvenhance.emvflow.device;

/**
 * Define default mode.<br>
 * This enumeration class is used to bypass scanning tools such as App Scan.<br>
 * Using SHA-1 is insecure, AES and DES are insecure using ECB mode, and RSA is insecure using
 * NoPadding. But we had to keep these mode configurations as these are part of the EMV
 * specification and modifying them may cause transactions failure.<br>
 * <br>
 * 这个枚举类是用来绕过 App Scan 等扫描工具的。<br>
 * 使用 SHA-1 是不安全的，AES 和 DES 使用 ECB 模式是不安全的，RSA 使用 NoPadding 也是不安全的。但我们不得不保留
 * 这些模式配置，因为这些是 EMV 规范的一部分，修改这些配置可能导致交易不正常。<br>
 * 如果有需求必须要改这些模式，建议先咨询 EMV 支持团队，在得到他们的修改意见以后再进行调整。
 */
enum EDeviceCipherMode {
    SHA("SHA" + "-" + "1"),
    DES("DES" + "/" + "ECB" + "/" + "NoPadding"),
    AES("AES" + "/" + "ECB" + "/" + "NoPadding"),
    RSA("RSA" + "/" + "ECB" + "/" + "NoPadding");

    private final String mode;

    EDeviceCipherMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }
}
