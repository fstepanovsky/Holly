# Holly

Webapp providing simple UI for downloading batches of images from Imageserver & Kramerius application stack.

## Building

run `gradle bootWar`, which creates war archive at `build/libs`

### Running without war

run `gradle bootRun`

## Configuration

Appliaction uses system environment variables to load its configuration

### Imageserver paths

Define `BASE_PATH_MZK`, `BASE_PATH_NDK` to provide paths to images on imageserver

### Output path

Define `BATCH_PATH`

### Conversion application

For conversions from default format (jp2 is expected as default for images on imageserver) define `JP2_TO_JPG_CONVERT`.
