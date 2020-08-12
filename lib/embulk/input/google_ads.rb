Embulk::JavaPlugin.register_input(
  "google_ads", "org.embulk.input.google_ads.GoogleAdsInputPlugin",
  File.expand_path('../../../../classpath', __FILE__))
