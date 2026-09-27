#
# Copyright (C) 2024-2026 Axion Widgets Port
# SPDX-License-Identifier: Apache-2.0
#

PRODUCT_SOONG_NAMESPACES += \
    packages/apps/AxionWidgets

# Features & Sysconfigs
PRODUCT_COPY_FILES += \
    packages/apps/AxionWidgets/configs/com.nothing.feature.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/com.nothing.feature.xml \
    packages/apps/AxionWidgets/configs/nothing-widgets-sysconfig.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/nothing-widgets-sysconfig.xml

PRODUCT_PACKAGES += \
    AxionWidgets \
    privapp_whitelist_com.android.axionwidgets \
    NothingWeather
