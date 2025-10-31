#!/bin/bash

proto_files=(
    # Language agnostic built ins
    "protobuf/src/google/protobuf/any.proto:./google/protobuf/any.proto"
    "protobuf/src/google/protobuf/api.proto:./google/protobuf/api.proto"
    "protobuf/src/google/protobuf/compiler/plugin.proto:./google/protobuf/compiler/plugin.proto"
    "protobuf/src/google/protobuf/descriptor.proto:./google/protobuf/descriptor.proto"
    "protobuf/src/google/protobuf/duration.proto:./google/protobuf/duration.proto"
    "protobuf/src/google/protobuf/empty.proto:./google/protobuf/empty.proto"
    "protobuf/src/google/protobuf/field_mask.proto:./google/protobuf/field_mask.proto"
    "protobuf/src/google/protobuf/source_context.proto:./google/protobuf/source_context.proto"
    "protobuf/src/google/protobuf/struct.proto:./google/protobuf/struct.proto"
    "protobuf/src/google/protobuf/timestamp.proto:./google/protobuf/timestamp.proto"
    "protobuf/src/google/protobuf/type.proto:./google/protobuf/type.proto"
    "protobuf/src/google/protobuf/wrappers.proto:./google/protobuf/wrappers.proto"
    # Java specific built ins
    "protobuf/java/core/src/main/resources/google/protobuf/java_features.proto:./google/protobuf/java_features.proto"
    "protobuf/java/core/src/main/resources/google/protobuf/java_mutable_features.proto:./google/protobuf/java_mutable_features.proto"
)
  
echo "Deleting old directories"
rm -rf ./protobuf
rm -rf ./google

echo "Cloning protobuf repository..."
git clone https://github.com/protocolbuffers/protobuf.git

echo "Copying proto files from repository..."
copied_count=0
missing_count=0
missing_files=()
for file in "${proto_files[@]}"; do
    IFS=':' read -r source_path dest_path <<< "$file"
    if [ -f "$source_path" ]; then
        mkdir -p "$(dirname "$dest_path")"
        cp "$source_path" "$dest_path"
        ((copied_count++))
    else
        missing_files+=("$file")
        ((missing_count++))
    fi
done

echo "Removing cloned repository..."
rm -rf ./protobuf

if [ $missing_count -gt 0 ]; then
    echo "Missing files:"
    for file in "${missing_files[@]}"; do
        echo "  - $file"
    done
    echo ""
    echo "ERROR: Not all files were found!"
    exit 1
else
    echo ""
    echo "SUCCESS: All proto files copied to ./google/protobuf/"
    exit 0
fi