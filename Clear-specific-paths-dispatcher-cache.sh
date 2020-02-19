PATHS="$PARAM_PATHS"
DISP_CACHE_PATH="/sites/dispatcher/cache"
IFS=$'
' PATHS_ARRAY=($PATHS)

for ARRAY_ITEM in "${PATHS_ARRAY[@]}"; do
  echo "Flushing $DISP_CACHE_PATH/$ARRAY_ITEM"
  sudo -u root -i rm -rf $DISP_CACHE_PATH/$ARRAY_ITEM;
done;