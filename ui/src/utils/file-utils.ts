export type SerializableFile = {
    name: string;
    dataUrl: string;
    type: string;
};

export function convertToSerializableFile(file: File): Promise<SerializableFile> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.readAsDataURL(file);
        reader.onload = () => {
            resolve({
                name: file.name,
                dataUrl: (reader.result as string | ArrayBuffer).toString(),
                type: file.type,
            });
        };
        reader.onerror = (error) => reject(error);
    });
}

export function convertToNativeFile(serializableFile: SerializableFile): File {
    const dataUrl = serializableFile.dataUrl;
    const arr = dataUrl.split(','),
        mimeMatch = arr[0].match(/:(.*?);/),
        mime = mimeMatch && mimeMatch[1],
        bstr = atob(arr[1]);
    if (!mime) {
        throw new Error('Invalid data URL');
    }
    let n = bstr.length;
    const u8arr = new Uint8Array(n);

    while (n--) {
        u8arr[n] = bstr.charCodeAt(n);
    }
    const blob = new Blob([u8arr], { type: mime });
    return new File([blob], serializableFile.name, {
        type: mime,
    });
}
