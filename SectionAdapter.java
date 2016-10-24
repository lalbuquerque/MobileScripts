public class SectionAdapter<Section, Item, SectionItemView extends View> extends RecyclerView.Adapter<SectionAdapter.SectionViewHolder> {
    private final Context context;
    private List<Section> sections;
    private List<Section> blacklist;
    private LayoutInflater inflater;
    private Section filter;

    @Nullable
    private Map<Section, List<Item>> data;
    private Func1<Section, String> sectionMapper;
    private Func2<Item, ItemPosition, SectionItemView> dataBinder;
    private Action3<SectionItemView, Item, ItemPosition> dataRebinder;
    private Action1<Item> onItemClick;
    private Action0 onSectionOpened;
    private Action0 onDataAvailable;
    private Action0 onNoDataAvailable;

    private Map<Section, Boolean> collapsedState;
    private Map<Section, Boolean> loadingState;
    private PublishSubject<CollapseEvent> sectionCollapseStatusStream = PublishSubject.create();

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean mIsLoadingItems;

    public SectionAdapter(Context context) {
        this(context, null, null);
    }

    public SectionAdapter(Context context, List<Section> excludedStatuses) {
        this(context, null, excludedStatuses);
    }

    public SectionAdapter(Context context, Map<Section, List<Item>> data) {
        this(context, data, null);
    }

    public SectionAdapter(Context context, @Nullable Map<Section, List<Item>> data, List<Section> blacklist) {
        this.context = context;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.data = data;
        this.blacklist = blacklist;
        this.sections = new ArrayList<>();

        validateItems(data);
    }

    @Override
    public int getItemCount() {
        if (data == null)
            return 0;

        if (filterIsValid()) {
            boolean hasFilteredData = data.get(filter).size() > 0;
            return hasFilteredData ? 1 : 0;
        } else {
            return sections.size();
        }
    }

    private boolean filterIsValid() {
        return filter != null && data != null && data.get(filter) != null;
    }

    @Override
    public SectionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.grouped_order_row, parent, false);
        return new SectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SectionAdapter.SectionViewHolder holder, int position) {
        Section section = filterIsValid() ? filter : sections.get(position);

        List<Item> items = null;

        if (data != null) {
            items = data.get(section);
        }

        holder.setSection(section);
        holder.setItems(items);
    }

    public void setOnItemClick(Action1<Item> onItemClick) {
        this.onItemClick = onItemClick;
    }

    public void setOnSectionOpened(Action0 onSectionOpened) {
        this.onSectionOpened = onSectionOpened;
    }

    public void setData(@Nullable Map<Section, List<Item>> sectionedItems, Map<Section, Boolean> collapsedState) {
        this.data = sectionedItems;
        this.collapsedState = collapsedState;

        new Thread(() -> {
            validateItems(sectionedItems);
            handler.post(this::notifyDataSetChanged);
        }).start();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setDataBinder(Func2<Item, ItemPosition, SectionItemView> dataBinder) {
        this.dataBinder = dataBinder;
    }

    public void setDataRebinder(Action3<SectionItemView, Item, ItemPosition> dataRebinder) {
        this.dataRebinder = dataRebinder;
    }

    public void setSectionMapper(Func1<Section, String> sectionMapper) {
        this.sectionMapper = sectionMapper;
    }

    public void setFilter(Section filter) {
        this.filter = filter;
        notifyDataSetChanged();
    }

    private void validateItems(Map<Section, List<Item>> data) {
        if (data == null) {
            return;
        }

        if (loadingState != null) {
            for (boolean isLoading : loadingState.values()) {
                if (isLoading) {
                    return;
                }
            }
        }

        if (blacklist != null) {
            for (Section excludedSection : blacklist) {
                data.remove(excludedSection);
            }
        }

        sections = new ArrayList<>();
        loadingState = new HashMap<>();

        for (Section section : data.keySet()) {
            if (data.get(section).size() > 0) {
                sections.add(section);
                loadingState.put(section, false);
            }
        }

        if (sections.size() > 0) {
            if (onDataAvailable != null) {
                handler.post(onDataAvailable::call);
            }
        } else {
            if (onNoDataAvailable != null) {
                handler.post(onNoDataAvailable::call);
            }
        }
    }

    public void subscribeSectionCollapseEvents(Action1<CollapseEvent> action) {
        sectionCollapseStatusStream.subscribe(action);
    }

    public void setOnDataAvailable(Action0 action0) {
        this.onDataAvailable = action0;
    }

    public void setOnNoDataAvailable(Action0 action0) {
        this.onNoDataAvailable = action0;
    }

    public enum ItemPosition {
        FIRST, MIDDLE, LAST
    }

    class SectionViewHolder extends RecyclerView.ViewHolder {
        private View mViewSectionHeader;
        private TextView mTextViewSectionHeaderTitle;
        private ImageView mImageViewSectionHeaderEndIcon;
        private ViewGroup mViewGroupSectionItems;
        private boolean mCollapsed = true;
        private Section mSection;

        public SectionViewHolder(View itemView) {
            super(itemView);
            mViewSectionHeader = itemView.findViewById(R.id.section_header);
            mTextViewSectionHeaderTitle = (TextView) itemView.findViewById(R.id.section_header_title);
            mImageViewSectionHeaderEndIcon = (ImageView) itemView.findViewById(R.id.section_header_end_icon);
            mViewGroupSectionItems = (ViewGroup) itemView.findViewById(R.id.section_items_container);
        }

        public void setSection(Section section) {
            String groupTitle;

            mSection = section;

            if (sectionMapper != null) {
                groupTitle = sectionMapper.call(section);
            } else {
                groupTitle = section.toString();
            }

            if (collapsedState != null) {
                mCollapsed = collapsedState.get(section) != null ? collapsedState.get(section) : mCollapsed;
            }

            mTextViewSectionHeaderTitle.setText(groupTitle);
            mViewSectionHeader.setOnClickListener(v -> {
                mCollapsed = !mCollapsed;
                dealWithSectionCollapseStatus(mCollapsed);

                sectionCollapseStatusStream.onNext(new CollapseEvent(section, mCollapsed));

                if (!mCollapsed && onSectionOpened != null) {
                    onSectionOpened.call();
                }
            });

            dealWithSectionCollapseStatus(mCollapsed);
        }

        private void dealWithSectionCollapseStatus(Boolean collapsed) {
            mViewGroupSectionItems.setVisibility(collapsed ? View.GONE : View.VISIBLE);
            mImageViewSectionHeaderEndIcon.setImageResource(collapsed ? R.drawable.ic_expand_more : R.drawable.ic_expand_less);
        }

        public void setItems(@Nullable List<Item> items) {
            loadingState.put(mSection, true);

            new Thread(() -> {
                if (dataBinder != null && dataRebinder != null && items != null) {
                    int childCount = mViewGroupSectionItems.getChildCount();
                    int itemsSize = items.size();

                    while (childCount > itemsSize) {
                        final int viewToRemoveIndex = childCount - 1;
                        handler.post(() -> mViewGroupSectionItems.removeViewAt(viewToRemoveIndex));
                        childCount--;
                    }

                    for (int i = 0; i < items.size(); i++) {
                        Item item = items.get(i);

                        ItemPosition itemPosition;

                        if (i == items.size() - 1) {
                            itemPosition = ItemPosition.LAST;
                        } else if (i == 0) {
                            itemPosition = ItemPosition.FIRST;
                        } else {
                            itemPosition = ItemPosition.MIDDLE;
                        }

                        SectionItemView child = (SectionItemView) mViewGroupSectionItems.getChildAt(i);

                        if (child == null) {
                            final View newChild = dataBinder.call(item, itemPosition);

                            if (newChild == null)
                                return;

                            handler.post(() -> {
                                mViewGroupSectionItems.addView(newChild);
                                newChild.setOnClickListener(v -> onItemClick.call(item));
                            });
                        } else {
                            handler.post(() -> {
                                dataRebinder.call(child, item, itemPosition);
                                child.setOnClickListener(v -> onItemClick.call(item));
                            });
                        }
                    }
                }

                loadingState.put(mSection, false);
            }).start();
        }
    }

    public class CollapseEvent {
        Section section;
        Boolean collapsed;

        CollapseEvent(Section section, boolean collapsed) {
            this.section = section;
            this.collapsed = collapsed;
        }

        public Section getSection() {
            return section;
        }

        public Boolean isCollapsed() {
            return collapsed;
        }
    }
}
