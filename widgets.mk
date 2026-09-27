#
# Copyright (C) 2024 Nothing Widgets Port
# SPDX-License-Identifier: Apache-2.0
#

# Permissions, Features & Sysconfigs
PRODUCT_COPY_FILES += \
    vendor/nothing/widgets/configs/com.nothing.feature.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/com.nothing.feature.xml \
    vendor/nothing/widgets/configs/privapp_permission_nothing_widgets.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp_permission_nothing_widgets.xml \
    vendor/nothing/widgets/configs/nothing-widgets-sysconfig.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/nothing-widgets-sysconfig.xml \
    vendor/nothing/widgets/configs/component-overrides-nothing-widgets.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/sysconfig/component-overrides-nothing-widgets.xml

# Inherit from widgets-vendor.mk
$(call inherit-product, vendor/nothing/widgets/widgets-vendor.mk)
