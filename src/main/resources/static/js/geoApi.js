
function getLocation(successHandler, errorHandler) {
  if (navigator.geolocation) {
    navigator.geolocation.watchPosition(successHandler, errorProcessorFactory(errorHandler), {
      enableHighAccuracy: true,
      maximumAge: 0
    });
  } else {
    errorHandler("Geolocation is not supported by this browser.");
  }
}

function errorProcessorFactory(errorHandler) {
  return function(error) {
    switch (error.code) {
      case error.PERMISSION_DENIED:
        errorHandler('Et ole antanut lupaa sijainnin lukemiseen. <a href="/geolocationInstructions">Ohjeet</a>');
        break;
      case error.POSITION_UNAVAILABLE:
        errorHandler('Sijainnin lukeminen epäonnistui. <a href="/geolocationInstructions">Ohjeet</a>');
        break;
      case error.TIMEOUT:
        errorHandler('Sijainnin lukeminen kesti liian kauan. Yritä lukea QR koodi uudestaan. <a href="/geolocationInstructions">Ohjeet</a>');
        break;
      case error.UNKNOWN_ERROR:
        errorHandler("An unknown error occurred.");
        break;
    }
  };
}

function distance(lat1, lon1, lat2, lon2) {
  if ((lat1 == lat2) && (lon1 == lon2)) {
    return 0;
  }
  else {
    var radlat1 = Math.PI * lat1/180;
    var radlat2 = Math.PI * lat2/180;
    var theta = lon1-lon2;
    var radtheta = Math.PI * theta/180;
    var dist = Math.sin(radlat1) * Math.sin(radlat2) + Math.cos(radlat1) * Math.cos(radlat2) * Math.cos(radtheta);
    if (dist > 1) {
      dist = 1;
    }
    dist = Math.acos(dist);
    dist = dist * 180/Math.PI;
    dist = dist * 60 * 1.1515;
    dist = dist * 1.609344 * 1000;
    return Math.round(dist);
  }
}