# geoleg

# Development

You need to copy the `.env_example` to `.env` and set the missing values. Use your own judgement how to load those environment variables when running the application.

## Request-response flow

Here is a typical request-response flow of the application:
1. Request for QR code on Geocaching.com site (`/qr/:qrcode`)
1. Response has redirect to init the scenario (`/engine/init/:scenario`)
   - Sets the state cookie `currentQuest=0`
1. Response shows the location reading page (though not needed) and automatic javaScript navigation to (`/engine/complete/:scenario/0/:secret/:location`)
   - Has the background story for the scenario
   - `Go`-button to start next quest
1. `Go`-button to make request for starting second quest (`/checkLocation?action=/engine/start/:scenario/1/:secret&lat=&lon=`)
1. After location reading request for starting the second quest (`/engine/start/:scenario/1/:secret/:location`) 
   - Shows the target coordinates (no timer for second quest)
   - Sets the state cookie `currentQuest=1`
1. Arriving at the on site QR-code, scanning it takes to complete the second quest (`/engine/complete/:scenario/1/:secret`)
1. Returns the location reading page that after location is read opens the actual complete URL (`/engine/complete/:scenario/:questOrder/:secret/:locationString`)

TODO: documenting the fact that complete url is not necassary to call technically, as only the start url will actualy change state, unless this is fixed by the check for quest DL passing while reading the complete text

## Admin pages
TODO: Document