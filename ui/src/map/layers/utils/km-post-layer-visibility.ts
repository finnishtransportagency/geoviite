export function shouldShowKmPostAtStep(kmNumber: string, step: number, isSelected: boolean) {
    return isSelected || (step > 0 && Number.parseInt(kmNumber) % step === 0);
}
