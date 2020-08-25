
function getLocation(successHandler, errorHandler) {
  if (navigator.geolocation) {
    navigator.geolocation.watchPosition(successHandler, errorProcessorFactory(errorHandler));
  } else {
    errorHandler("Geolocation is not supported by this browser.");
  }
}

function errorProcessorFactory(errorHandler) {
  return function(error) {
    switch (error.code) {
      case error.PERMISSION_DENIED:
        errorHandler("User denied the request for Geolocation.");
        break;
      case error.POSITION_UNAVAILABLE:
        errorHandler("Location information is unavailable.");
        break;
      case error.TIMEOUT:
        errorHandler("The request to get user location timed out.");
        break;
      case error.UNKNOWN_ERROR:
        errorHandler("An unknown error occurred.");
        break;
    }
  };
}
