import { connect } from 'react-redux';
import React from 'react';
import ElementListView from 'data-products/element-list/element-list-view';

const DataProductsMainContainer = connect()(ElementListView);

export const ElementListContainerWithProvider: React.FC = () => {
    return <DataProductsMainContainer />;
};
