import { connect } from 'react-redux';
import React from 'react';
import VerticalGeometryView from 'data-products/vertical-geometry/vertical-geometry-view';

const DataProductsMainContainer = connect()(VerticalGeometryView);

export const VerticalGeometryContainerWithProvider: React.FC = () => {
    return <DataProductsMainContainer />;
};
