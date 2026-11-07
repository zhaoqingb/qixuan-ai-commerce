export interface CategoryTreeNode{
    id: number
    parentId: number | null
    name: string
    sortOrder: number
    children:CategoryTreeNode[]
}
