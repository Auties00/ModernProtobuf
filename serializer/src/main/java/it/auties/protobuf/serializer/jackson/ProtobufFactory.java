package it.auties.protobuf.serializer.jackson;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import it.auties.protobuf.serializer.util.VersionInfo;
import lombok.NoArgsConstructor;

import java.io.*;
import java.net.URL;
import java.util.Arrays;

@NoArgsConstructor
class ProtobufFactory extends JsonFactory {
    protected ProtobufFactory(ProtobufFactory src, ObjectCodec oc) {
        super(src, oc);
    }

    @Override
    public ProtobufFactory copy() {
        _checkInvalidCopy(ProtobufFactory.class);
        return new ProtobufFactory(this, null);
    }

    @Serial
    @Override
    protected Object readResolve() {
        return new ProtobufFactory(this, _objectCodec);
    }

    @Override
    public Version version() {
        return VersionInfo.current();
    }

    @Override
    public boolean requiresPropertyOrdering() {
        return false;
    }

    @Override
    public boolean canHandleBinaryNatively() {
        return true;
    }

    @Override
    public boolean canUseCharArrays() {
        return false;
    }

    @Override
    public ProtobufParser createParser(File f) throws IOException {
        var context = _createContext(_createContentReference(f), true);
        return _createParser(_decorate(new FileInputStream(f), context), context);
    }

    @Override
    public ProtobufParser createParser(URL url) throws IOException {
        var context = _createContext(_createContentReference(url), true);
        return _createParser(_decorate(_optimizedStreamFromURL(url), context), context);
    }

    @Override
    public ProtobufParser createParser(InputStream in) throws IOException {
        var context = _createContext(_createContentReference(in), false);
        return _createParser(_decorate(in, context), context);
    }

    @Override
    public ProtobufParser createParser(byte[] data) {
        return createParser(data, null);
    }

    public ProtobufParser createParser(byte[] data, ProtobufSchema schema) {
        return _createParser(data, 0, data.length, _createContext(_createContentReference(data), true), schema);
    }


    @Override
    public ProtobufParser createParser(byte[] data, int offset, int len) throws IOException {
        var context = _createContext(_createContentReference(data, offset, len), true);
        if (_inputDecorator == null) {
            return _createParser(data, offset, len, context);
        }

        var in = _inputDecorator.decorate(context, data, 0, data.length);
        if (in != null) {
            return _createParser(in, context);
        }

        return _createParser(data, offset, len, context);
    }

    @Override
    protected IOContext _createContext(ContentReference contentRef, boolean resourceManaged) {
        return super._createContext(contentRef, resourceManaged);
    }

    @Override
    protected ProtobufParser _createParser(InputStream in, IOContext context) {
        var buf = context.allocReadIOBuffer();
        return new ProtobufParser(context, _parserFeatures, _objectCodec, buf);
    }

    @Override
    protected JsonParser _createParser(Reader r, IOContext context) {
        return _nonByteSource();
    }

    @Override
    protected JsonParser _createParser(char[] data, int offset, int len, IOContext context, boolean recyclable) {
        return _nonByteSource();
    }

    @Override
    protected ProtobufParser _createParser(byte[] data, int offset, int len, IOContext context) {
        return _createParser(data, offset, len, context, null);
    }

    protected ProtobufParser _createParser(byte[] data, int offset, int len, IOContext context, ProtobufSchema schema) {
        var parser = new ProtobufParser(context, _parserFeatures, _objectCodec, Arrays.copyOfRange(data, offset, offset + len));
        parser.setSchema(schema);
        return parser;
    }

    @Override
    protected Writer _createWriter(OutputStream out, JsonEncoding enc, IOContext context) {
        return _nonByteTarget();
    }

    protected <T> T _nonByteSource() {
        throw new UnsupportedOperationException("Can not create parser for non-byte-based source");
    }

    protected <T> T _nonByteTarget() {
        throw new UnsupportedOperationException("Can not create generator for non-byte-based target");
    }

    @Override
    public ProtobufGenerator createGenerator(OutputStream out, JsonEncoding enc) throws IOException {
        var context = _createContext(_createContentReference(out), false);
        context.setEncoding(enc);
        return _createProtobufGenerator(_generatorFeatures, _objectCodec, _decorate(out, context));
    }

    @Override
    public ProtobufGenerator createGenerator(OutputStream out) throws IOException {
        var context = _createContext(_createContentReference(out), false);
        return _createProtobufGenerator(_generatorFeatures, _objectCodec, _decorate(out, context));
    }

    @Override
    protected ProtobufGenerator _createUTF8Generator(OutputStream out, IOContext context) {
        return _createProtobufGenerator(_generatorFeatures, _objectCodec, out);
    }

    private ProtobufGenerator _createProtobufGenerator(int stdFeat, ObjectCodec codec, OutputStream out) {
        return new ProtobufGenerator(stdFeat, codec, out);
    }

    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return schema instanceof ProtobufSchema;
    }
}