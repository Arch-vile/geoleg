/**
 * URLSearchParams does not work on all IE (e.g. IE 11) versions. Need to use this home brewed
 * solution instead.
 */
function getQueryParam(name){
  const results = new RegExp('[\?&]' + name + '=([^&#]*)').exec(window.location.href);
  if (results == null){
    return null;
  }
  else {
    return decodeURI(results[1]) || 0;
  }
}