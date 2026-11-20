# Cleanup audit

Removed legacy fallback drawing and its resource-loading path, keeping `drawMasterV15()` as the sole renderer.

Removed 44 image assets: n14_dock_card.png, n14_time_card_carbon.png, n14_nav_rail.png, n14_clock_face_carbon.png, premium_icon_8.png, carbon_side_panel.png, n14_music_card_day.png, n14_nav_selected_day.png, n14_scene_day.jpg, n14_dock_card_day.png, n14_music_card_carbon.png, carbon_dock_panel.png, premium_icon_4.png, carbon_music_panel.png, n14_time_card_day.png, n14_source_card.png, carbon_gauge_panel.png, n14_nav_selected_carbon.png, premium_icon_7.png, n14_clock_face.png, n14_header_day.png, premium_icon_3.png, n14_scene.jpg, premium_icon_6.png, n14_scene_carbon.jpg, n14_nav_selected.png, n14_source_card_day.png, n14_time_card.png, premium_icon_0.png, n14_music_card.png, premium_icon_1.png, v147_car_scene_pearl.png, v147_car_scene_electric.png, v147_car_scene_carbon.png, n14_source_card_carbon.png, premium_icon_2.png, premium_icon_5.png, carbon_analog_dial.png, n14_header.png, n14_clock_face_day.png, n14_nav_rail_day.png, n14_nav_rail_carbon.png, n14_dock_card_carbon.png, n14_header_carbon.png.

Retained 3 referenced image assets: master_pearl.jpg, master_electric.jpg, master_carbon.jpg.

Checks: static resource references and ZIP integrity. **Not verified**: Gradle compilation, runtime touch accuracy, USB resume, factory Bluetooth package discovery, music metadata and progress, or actual head-unit performance.
